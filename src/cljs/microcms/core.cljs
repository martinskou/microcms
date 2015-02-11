(ns microcms.core
    (:require [clojure.string :as string]
              [cljs.reader :as reader]
              [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
              [ajax.core :refer [GET POST PUT DELETE]])
    (:import goog.History))

;; -------------------------
;; Views


(def state (atom {:epage {}  :pages [] :saved? false}))

(defn set-value! [id value]
  (swap! state assoc :saved? false)
  (swap! state assoc-in [:epage id] value))

(defn get-value [id]
  (get-in @state [:epage id]))

(defn reset-location! [fragment] ;; eg "#/home"
  (set! (.-href (.-location js/document)) fragment))


(GET "/cms/api/pages/" {:handler (fn [x] (swap! state assoc :pages x))})

(defn find-id-in-vector [v id]
  ; (first (first (filter #(= (second %) 2) (map-indexed vector [1 1 3 4 5]))))
  (first (first (filter #(= (:id (second %)) id) (map-indexed vector v))))  
  )

(defn save-edited-page []
  (let [doc (:epage @state)]
    (if (nil? (get doc :id))
      (POST "/cms/api/pages/"
          {:params (:epage @state)
           :format :edn
           :handler (fn [new-id] 
                      (.log js/console "new-id" new-id)
                      (swap! state update-in [:pages] conj 
                                                      (assoc (:epage @state) :id new-id))
                      (swap! state assoc-in [:epage :id] new-id)
                      (swap! state assoc :saved? true))})
      (PUT (str "/cms/api/pages/" (get doc :id) "/")
          {:params (:epage @state)
           :format :edn
           :handler (fn [_] 
                      (.log js/console "update-index" (find-id-in-vector (:pages @state) (get doc :id))  )
                      (swap! state update-in [:pages] assoc (find-id-in-vector (:pages @state) (get doc :id)) doc)
                      (swap! state assoc :saved? true))})
      )))


(defn text-input [label id symid fieldtype]
  [:div.row
    [:div {:className "large-4 columns"} [:span label]]
    [:div {:className "large-8 columns"} [fieldtype {:type "text" :class "form-control"
                                                     :value (get-value symid)
                                                     :onChange #(set-value! symid (-> % .-target .-value))}]]])

(defn page-editor []
  [:div [:h2 "Edit page"]
    [text-input "Title" "title" :title :input]
    [text-input "URL" "url" :url :input]
    [text-input "Teaser" "teaser" :teaser :textarea]
        [:button {:type "submit"
                  :class (str "btn btn-default" (when (:saved? @state) " disabled") )
                  :onClick save-edited-page} "save"]])

(defn pages-list []
  [:div [:h2 "Page list"]
   [:div {:className "large-12 columns"}
   [:ul
   (for [item (@state :pages)] ^{:id item}
       [:li "ID:" (item :id) [:span " / "] (item :title) [:span " / "] 
            [:a {:href (str "#/delete/" (item :id))} "delete"] [:span " / "]
            [:a {:href (str "#/edit/" (item :id))} "edit"]])]]])

(defn layout [menu content]
  [:div {:className "row"}
    [:div {:className "large-3 columns"} menu]
    [:div {:className "large-9 columns"} content]])

(defn menu []
  [:div
   [:div [:a {:href "#/"} "front"]]
   [:div [:a {:href "#/about"} "about"]]
   [:div [:a {:href "#/list"} "list"]]
   [:div [:a {:href "#/new"} "create"]]])

(defn home-page []
  (layout [menu] 
          [:div [:h2 "Welcome to microcms"]
          [:p "You've been hit by, you've been struck by, a smooth CMS."]]))

(defn about-page []
  [layout [menu]
          [:div [:h2 "About microcms"]
                [:p "A very small CMS, in fact, it's a microcms."]
                [:p "Made as a Clojure / Clojurescript / Reagent test case."]]])

(defn new-page []
  [layout [menu]
          [page-editor]])

(defn list-pages []
  [layout [menu]
          [pages-list]])

(defn delete-by-id [id]
  (swap! state update-in [:pages] 
         (fn [ov] (into [] (remove #(= (reader/read-string id) (% :id)) ov))))
)


(defn delete-page [id]
  (.log js/console "Delete" id)

  (DELETE (str "/cms/api/pages/" id "/")
        {:params {:id id}
         :format :edn
         :handler (fn [result] 
                    (.log js/console "delete" result)
                    (delete-by-id id))})
    
  )

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page home-page))

(secretary/defroute "/about" []
  (session/put! :current-page about-page))

(secretary/defroute "/new" []
  (session/put! :current-page new-page))

(secretary/defroute "/list" []
  (session/put! :current-page list-pages))

(secretary/defroute "/delete/:id" {id :id}
  (delete-page id)
  (reset-location! "#/list"))

(secretary/defroute "/edit/:id" {id :id}
  (let [idx (find-id-in-vector (:pages @state) (reader/read-string id))]
    (.log js/console "index" idx)
    (swap! state assoc :epage (nth (:pages @state) idx) )
    (reset-location! "#/new")))

  

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))


(defn blanks [i]
  (apply str (repeat i \space)))

(defn show-data [data level]
  (string/join "" (flatten
    (cond
     (vector? data) [(blanks level) "[\n"
        (doall (into [] (map #(show-data % (+ level 1)) data)))
        (blanks level) "]\n"]
     (map? data) [(blanks level) "{\n"
        (doall (map #(string/join " " [(blanks level) (first %) (show-data (last %) (+ level 1))]) data))
        (blanks level) "}\n"]
     :else [(pr-str data) "\n"]
     ))))

(defn debug-component []
  (let [data @state]
    [:div
      [:pre (show-data data 0)]]))


;; -------------------------
;; Initialize app
(defn init! []
  (hook-browser-navigation!)
  (reagent/render-component [current-page] (.getElementById js/document "app"))
  (reagent/render-component [debug-component] (. js/document (getElementById "debug")))
)
