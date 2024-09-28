(ns rekrvn.modules.db 
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [rekrvn.config :refer [db-file]]
            [clojure.tools.logging :as log]))

;; goodbye mongo hello sqlite

(def modName "db")

(def db (jdbc/get-datasource {:jdbcUrl db-file}))

(defn- fix-keys [m]
  ;; library keys are all namespaced ex {:memos/channel "example"} instead of {:channel "example"}
  (update-keys m (comp keyword name)))

(defn insert! [from document]
  ;; from is the module doing the storing eg. "caps" or "quotes"
  (log/info "db insert" from document)
  (sql/insert! db (keyword from) document))

;(defn update! [from new-doc finder]
; no use for this currently 
;  (sql/update! db (keyword from) new-doc finder))

(defn remove! [from finder]
  ;; will remove ALL documents in the collection that match finder
  ;; modules calling (remove ..) do so at their own risk
  (log/info "db remove" from finder)
  (sql/delete! db (keyword from) finder))

(defn get-rand-as-map [from finder]
  ;; gets a random document matching the finder map
  (log/info "db get-rand" from finder)
  (let [res (sql/find-by-keys db (keyword from) finder {:order-by [(keyword "random()")] :limit 1})]
    (fix-keys (first res))))

(defn get-all-docs [from finder]
  ;; returns all documents matching finder
  (log/info "db get-all-docs" from finder)
  (map fix-keys (sql/find-by-keys db (keyword from) finder)))
