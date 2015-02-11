(ns microcms.handler
  (:require [microcms.dev :refer [browser-repl start-figwheel]]
            [compojure.core :refer [GET POST PUT DELETE ANY defroutes context]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.defaults :refer [site-defaults api-defaults wrap-defaults]]
            
            [ring.middleware.params :as params]
            [ring.middleware.edn :as rme]
            [ring.middleware.file :as rmf]
            [ring.middleware.multipart-params :as rmmp]
            
;            [selmer.parser :refer [render-file]]
            [environ.core :refer [env]]
            [prone.middleware :refer [wrap-exceptions]]
            [korma.core :refer :all]
            [korma.db :refer :all]
;            [clojure.java.jdbc :as sql]
;            [noir.response :refer [edn]]
            [clojure.pprint :refer [pprint]]
            [hiccup.core :refer :all]
            [hiccup.page :refer :all]
            [markdown.core :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]
            
            [microcms.model :as model]
            
            ))


;(def db-config {:classname   "org.h2.Driver"
;                :subprotocol "h2"
;                :subname     "resources/db/cms.db"})

;(defdb db db-config)



(defn create-page [page]
  ; {:generated_key 2}
  (:generated_key (insert :pages
                          (values page))))

(defn update-page [id page]
   (update :pages (set-fields page)
                 (where {:id [= id]})))

(defn get-page [url]
  (first (select :pages
                 (where {:url url})
                 (limit 1))))

(defn get-pages []
  (into [] (select :pages)))



(defn save-document []
  (pprint "save!")
  {:status 200 :body "ok"}
  )

(defn edn-response [data & [status]]
  (println "edn-response")
  (pprint data)
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn response [data & [status]]
  (println "response")
  (pprint data)
  {:status (or status 200)
   :headers {"Content-Type" "test/html"}
   :body (pr-str data)})

(defn page-menu []
  [:ul
   (for [p (get-pages)]
     [:li 
      [:a {:href (:url p)} (:title p)]])])

(defn page-template [page]
  (html5 
    {:lang "en"} 
    [:head (include-css "/foundation-5.5.0/css/normalize.css")
           (include-css "/foundation-5.5.0/css/foundation.min.css")
           (include-css "/css/site.css")]
    [:body [:div {:class "row"} 
              [:div {:class "large-12 columns"} 
                [:div {:class "sticky"}  
                  [:nav {:class "top-bar" :data-topbar "" :role "navigation" :data-options "sticky_on: large" }
                    [:ul {:class "title-area"} 
                     [:li {:class "name"} [:h1 [:a {:href "/"} "site"]]]  
                     [:li {:class "toggle-topbar menu-icon"} [:a {:href "#"} [:span ] ] ]
                     ]
                    [:section {:class "top-bar-section"}
                     [:ul {:class "left"}
                       (for [p (get-pages)]
                         [:li [:a {:href (:url p)} (:title p)]])]]]]]
              [:div {:class "large-12 columns"} 
                  [:h1 (page :title)]
                  [:h2 (page :teaser)]
                  (md-to-html-string (page :teaser) :code-style #(str "class=\"brush: " % "\""))
                  ]]
        (include-js "/jquery.js") 
        (include-js "/foundation-5.5.0/js/foundation.min.js")
        [:script "$(document).foundation();"] 
     ]))

(defn cms-template [dev]
  (html5 
    {:lang "en"} 
    [:head (include-css "/foundation-5.5.0/css/normalize.css")
           (include-css "/foundation-5.5.0/css/foundation.min.css")
           (if dev
             (include-css "/css/cms.css")
             (include-css "/css/cms.min.css"))] 
    [:body [:div {:id "app"}]
           [:div {:id "debug"}]
           (include-js "/jquery.js") 
           (include-js "/foundation-5.5.0/js/foundation.min.js")
           (if dev
              (list 
                 (include-js "/react.js") 
                 (include-js "/js/out/goog/base.js") 
                 (include-js "/js/app.js") 
                 [:script {:type "text/javascript"} "goog.require(\"microcms.dev\");"])
              (list 
                 (include-js "/react.min.js") 
                 (include-js "/js/app.js")))]))


(defn page-or-404 [url]
  (println "page-or-404..." url)
  (let [page (get-page url)]
    (if (nil? page)
      {:status 404 :body (page-template {:title "404" :content "No can do, try using the menu."}) }  
      {:status 200 :body (page-template page) })))

(defn save-page [id title url teaser]
  (println "save-page" id)
  (edn-response
    (if (nil? id)
      (create-page {:title title :url url :teaser teaser})
      (update-page id {:title title :url url :teaser teaser}))))

(defn delete-page [id]
  (delete :pages (where {:id [= id]}))
  (edn-response {:status "ok"}))

(defn list-pages []
  (edn-response (get-pages)))

(defroutes routes
  ; Onepage CMS app:
  (GET "/cms/" [] (cms-template (env :dev?)))
  
  ; REST API for pages model:
  (context "/cms/api/pages" []
    (GET "/" [] (list-pages))  ; list pages
    (GET "/:id/" [id] (get-page id))  ; get single page
    (POST "/" [title url teaser] (save-page nil title url teaser)) ; insert new page
    (PUT "/:id/" [id title url teaser] (save-page id title url teaser))) ; update page

  ; Database helper functions  
  (when (env :dev?) (defroutes db-routes
    (GET "/cms/db/update" [] (model/db-create false))  
    (GET "/cms/db/clear" [] (model/db-create true))))  
  
  ; Serve css, js, images, etc. from ressource/public
  (resources "/")
  
  ; Server CMS pages
  (ANY "*" [:as {u :uri}] (page-or-404 u))
)

(defn init []
  (println "ring init")
  (model/db-create false))

(def app (-> routes
               rme/wrap-edn-params
               params/wrap-params
               rmmp/wrap-multipart-params))  
