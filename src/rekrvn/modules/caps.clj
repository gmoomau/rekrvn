(ns rekrvn.modules.caps
  (:refer-clojure :exclude [sort find])
  (:require [rekrvn.hub :as hub]
            [monger.core :as mg]
            [monger.collection :as mc])
  (:use monger.query)
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]))

(def modName "caps")

(defn caps [[channel line] reply]
  (when (> (count line) 3)
    (do
      (mg/connect!)
      (mg/set-db! (mg/get-db "rekrvn"))
      (let [new-doc {:_id (ObjectId.) :text line :channel channel}
            ;; find a random caps sentence
            ;; the (skip ...) is slow, but the alternative (adding a "random" field
            ;; to the documents and choosing the doc with a "random" closest to
            ;; some other random number) is not uniformly random
            num-lines (mc/count "caps" {:channel channel})
            result (with-collection "caps"
                                    (find {:channel channel})
                                    (skip (rand num-lines))
                                    (limit 1))]
        (mc/insert "caps" new-doc)

        (when-let [out-line (first result)]
          ;; result is a lazy seq of docs
          (reply modName (:text out-line)))
        (mg/disconnect!)))))

(hub/addListener modName #"^irc.*PRIVMSG #(\S+) :([^a-z]*[A-Z]+[^a-z]*)$" caps)
