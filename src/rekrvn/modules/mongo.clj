(ns rekrvn.modules.mongo
  (:require [monger.collection :as mc]
            [monger.core :as mg]
            [monger.query :as mq])
  (:import (org.bson.types ObjectId))
  (:refer-clojure :exclude [find remove sort]))

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

(defn remove [from finder]
  ;; will remove ALL documents in the collection that match finder
  ;; modules calling (remove ..) do so at their own risk
  (mc/remove from finder))

(defn get-rand-as-map [from finder]
  ;; assumes that (connect!) has been called
  ;; gets a random document matching the finder map
  ;; (skip ...) is slow, but the alternative (involving a "random"
  ;; field for all documents) is not uniformly random
  (let [num-docs (mc/count from finder)]
    ;; with-collection returns a list
    (first (mq/with-collection from
                            (mq/find finder)
                            (mq/skip (rand num-docs))
                            (mq/limit 1)))))

(defn get-docs [from finder]
   ;; returns all documents matching finder
   (mc/find-maps from finder))
