(ns rekrvn.modules.memos
  (:require [rekrvn.hub :as hub]
            [rekrvn.modules.mongo :as mongo]))

(def modName "memos")
;; a module for leaving memos for other users
;; keeps a set of users with outstanding memos in-memory and stores the
;; memos in mongodb

(def targets (ref #{}))

(defn niceify [memo]
  ;; :sender :channel :recip :msg
  (str (:to memo) ", " (:from memo) " says: " (:msg memo)))


(defn add-memo [[sender channel recip msg] reply]
  (dosync
    (alter targets conj (clojure.string/lower-case recip))
    (mongo/connect!)
    (mongo/insert modName {:from sender :to recip :channel channel :msg msg})
    (mongo/disconnect!)
    (reply modName "memo'd")))


(defn deliver-memos [[nick channel] reply]
  (dosync
    (when (@targets (s/lower-case nick))
      (let [memo-finder {:to (re-pattern (str "(?i)^" nick "$")) :channel channel}]
        (mongo/connect!)
        (doseq [memo (mongo/get-docs modName memo-finder)]
          (reply modName (niceify memo)))
        (mongo/remove modName memo-finder)
        (mongo/disconnect!)
        (alter targets disj (clojure.string/lower-case nick))))))

(defn memo-list []
  (dosync
    (mongo/connect!)
    (ref-set targets
             (into #{} (map (comp clojure.string/lower-case :to) (mongo/get-docs modName {}))))
    (mongo/disconnect!)))

(memo-list)
(hub/addListener modName #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.memo (\S+) (.*)" add-memo)
(hub/addListener modName #"^irc :(\S+)!\S+ PRIVMSG (\S+) :.*" deliver-memos)

