(ns microcms.model
  (:require [korma.core :refer :all]
            [korma.db :refer :all]
            [clojure.java.jdbc :as sql]
            [clojure.pprint :refer [pprint]]
            ))


(def cms-schema {
     :pages {:name "pages"
           :fields [  [:created_at :timestamp "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]
                      [:modified_at :timestamp "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]

                      [:id "MEDIUMINT NOT NULL AUTO_INCREMENT" :primary :key]
                      [:fkey_parent :int]
                      [:idx :int]

                      [:inherit_type "varchar(100)"] ; (P)arent,(I)index,In(H)erit,(S)tate,(V)ariant,(T)emplate,(C)ontent
                      [:fkey_inherit_from :int]
                      
                      [:state "varchar(100)"]
                      [:variant "varchar(100)"]
                      [:template "varchar(200)"]
                      
                      [:url "varchar(200)"]
                      [:slug "varchar(100)"]

                      [:caption "varchar(60)"]
                      [:title "varchar(200)"]
                      [:subtitle "varchar(200)"]
                      [:teaser "text"]

                      [:thumbnail "varchar(200)"]

                      [:keyword "text"]
                      [:metatags "text"] ] } 
   
   :content {:name "content"
             :fields [  [:created_at :timestamp "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]
                        [:modified_at :timestamp "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]

                        [:id "MEDIUMINT NOT NULL AUTO_INCREMENT" :primary :key]
                        [:fkey_page_id :int]
                        [:idx :int]
                        
                        [:grp "varchar(100)"]
                        [:caption "varchar(60)"]
                        [:title "varchar(200)"]
                        [:subtitle "varchar(200)"]
                        [:teaser "text"]
                        [:text "text"]

                        [:image_1 "varchar(200)"]
                        [:image_1_alt "varchar(200)"]
                        [:image_1_link "varchar(200)"]

                        [:link_1 "varchar(200)"]
                        [:link_1_text "varchar(200)"]
                        
                        [:data "text"]
                        
                         ] }
   :user {:name "user"
          :fields [ [:created_at :timestamp "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]
                    [:modified_at :timestamp "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]

                    [:id "MEDIUMINT NOT NULL AUTO_INCREMENT" :primary :key]
                        
                    [:email "varchar(100)"]
                    [:salt "varchar(100)"]
                    [:digest "varchar(100)"]
                   
                   ]}
   })

(def cms-db-map (assoc (mysql {:db "microcms"
                           :user "microcms"
                           :password "microcms"})
                           :schema cms-schema))

; a db-map is a map with connection and schema information. No reason to keep them seperate.

;(def cms-korma-db cms-db-map) 


(defdb cms-korma-db cms-db-map)
;(defentity pages)

;;;
;;; JDBC Functions
;;;
;;; A small "migration" framework.
;;;

(defn db-exists-table? 
  ([table-name] (db-exists-table? table-name cms-db-map))
  ([table-name db-map] (not (empty? (sql/query db-map (str "SHOW TABLES LIKE '" table-name "'"))))))

(defn db-exists-field? 
  ([table field] (db-exists-field? table field cms-db-map))
  ([table field db-map] (not (empty? (sql/query db-map (str "SELECT * FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='microcms' AND TABLE_NAME='" table "' AND COLUMN_NAME='" field "'"))))))
  
(defn db-create-table 
  ([table] (db-create-table table cms-db-map))
  ([table db-map]
  (sql/db-do-commands db-map
    (println "CREATE" (get table :name))
    (apply sql/create-table-ddl (cons (get table :name) (get table :fields))))))

(defn db-drop-table 
  ([table-name] (db-drop-table table-name cms-db-map))
  ([table-name db-map]
  (println "DROP" table-name)
  (when (db-exists-table? table-name)
    (sql/db-do-commands db-map
       (sql/drop-table-ddl table-name)))))

(defn db-create-fields [table]
  (let [tablename (table :name)
        field (last (table :fields))
        fieldname (subs (str (first field)) 1)
        fieldtype (second field)]
      (when (not (db-exists-field? tablename fieldname))
        (do     
          (println "ALTER" tablename fieldname)
          (sql/db-do-commands cms-db-map (str "ALTER TABLE `" tablename "` ADD `" fieldname "` " fieldtype ))))))  
  
(defn db-create [drop?]
  ; create or update definition of all table and fields in schema
  (when drop?
    (doall (map #(db-drop-table (get-in cms-db-map [:schema % :name])) (keys (cms-db-map :schema)))))
  (defn update-table [tablemap]
      (if (not (db-exists-table? (tablemap :name)))
        (db-create-table tablemap)
        (db-create-fields tablemap)))
  (doall (map #(update-table (get-in cms-db-map [:schema %])) (keys (cms-db-map :schema)))))

