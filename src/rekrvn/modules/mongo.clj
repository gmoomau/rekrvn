(ns rekrvn.modules.mongo
  (:refer-clojure :exclude [sort find])
  (:require [monger.core :as mg]
            [monger.collection :as mc])
  (:use monger.query)
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]))

;; pretty thin wrapper around monger
;; basically only exists for get-rand-as-map

(def modName "mongo")

(defn connect! []
  (do
    (mg/connect!)
    (mg/set-db! (mg/get-db "rekrvn"))))

(defn disconnect! []
  (mg/disconnect!))

(defn insert [from document]
  ;; ex. from could be "caps" or "quotes"
  (mc/insert from (merge document {:_id (ObjectId.)})))

(defn get-rand-as-map [from finder]
  ;; assumes that (connect!) has been called
  ;; gets a random document matching the finder map
  ;; (skip ...) is slow, but the alternative (involving a "random"
  ;; field for all documents) is not uniformly random
  (let [num-docs (mc/count from finder)]
    ;; with-collection returns a list
    (first (with-collection from
                            (find finder)
                            (skip (rand num-docs))
                            (limit 1)))))