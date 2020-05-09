(ns rekrvn.modules.memos
  (:require [rekrvn.hub :as hub]
            [rekrvn.modules.mongo :as mongo]))

(def mod-name "memos")
;; a module for leaving memos for other users
;; keeps a set of users with outstanding memos in-memory and stores the
;; memos in mongodb

(def targets (ref #{}))
;; holds a set of [nick channel] vectors
;; TODO: include network in key. existing memos will need to be updated

(defn niceify [memo]
  ;; :sender :channel :recip :msg
  (str (when-let [timestamp (:time memo)] (str "(" timestamp ") "))
       (:to memo) ", " (:from memo) " says: " (:msg memo)))

(defn chatter
  ;; turns stuff into [nick channel] vecs for use with @targets
  ([nick channel] ; from strings
   [(clojure.string/lower-case nick) channel])
  ([memo] ; from a map pulled from the db
   [(clojure.string/lower-case (:to memo)) (:channel memo)]))

(defn add-memo [[sender channel recip msg] reply]
  (dosync
    (alter targets conj (chatter recip channel))
    (mongo/connect!)
    (mongo/insert mod-name {:from sender
                           :to recip
                           :channel channel
                           :msg msg
                           :time (str (new java.util.Date))})
    (mongo/disconnect!)
    (reply mod-name "memo'd")))

(defn deliver-memos [[nick channel] reply]
  (dosync
    (when (@targets (chatter nick channel))
      (let [memo-finder {:to (re-pattern (str "(?i)^" nick "$")) :channel channel}]
        (mongo/connect!)
        (doseq [memo (mongo/get-docs mod-name memo-finder)]
          (reply mod-name (niceify memo)))
        (mongo/remove mod-name memo-finder)
        (mongo/disconnect!)
        (alter targets disj (chatter nick channel))))))

(defn memo-list []
  (dosync
    (mongo/connect!)
    (ref-set targets (into #{} (map chatter (mongo/get-docs mod-name {}))))
    (mongo/disconnect!)))

(memo-list)
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.(?:tell|memo|remind) (\S+) (.*)" add-memo)
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :.*" deliver-memos)

