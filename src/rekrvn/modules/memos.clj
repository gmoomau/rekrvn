(ns rekrvn.modules.memos
  (:require [rekrvn.hub :as hub]
            [rekrvn.modules.db :as db]))

(def mod-name "memos")
;; a module for leaving memos for other users
;; keeps a set of users with outstanding memos in-memory and stores the
;; memos in sqlite

(def targets (ref #{}))
;; holds a set of [nick channel] vectors
;; TODO: include network in key. existing memos will need to be updated

(defn niceify [memo]
  ;; :sender :channel :recip :msg
  (str (when-let [timestamp (:time memo)] (str "(" timestamp ") "))
       (:recip memo) ", " (:sender memo) " says: " (:msg memo)))

(defn chatter
  ;; turns stuff into [nick channel] vecs for use with @targets
  ([nick channel] ; from strings
   [(clojure.string/lower-case nick) channel])
  ([memo] ; from a map pulled from the db
   [(clojure.string/lower-case (:recip memo)) (:channel memo)]))

(defn add-memo [[sender channel command recip msg] reply]
  (dosync
    (alter targets conj (chatter recip channel))
    (db/insert! mod-name {:sender sender
                          :recip recip
                          :channel channel
                          :msg msg
                          :time (str (new java.util.Date))})
    (let [confirmation (case command
                         "tell"   "tol'd"
                         "remind" "remoun'd"
                         ;default
                         "memo'd")]
      (reply mod-name confirmation))))

(defn deliver-memos [[nick channel] reply]
  ; only works if the db search is case-insensitive
  (dosync
    (when (@targets (chatter nick channel))
      (let [memo-finder {:recip (clojure.string/lower-case nick) :channel channel}]
        (doseq [memo (db/get-all-docs mod-name memo-finder)]
          (reply mod-name (niceify memo)))
        (db/remove! mod-name memo-finder)
        (alter targets disj (chatter nick channel))))))

(defn memo-list []
  ;; why is this a function and why isn't it just done in targets?
  (dosync
    (ref-set targets (into #{} (map chatter (db/get-all-docs mod-name :all))))))

(memo-list)
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.(tell|memo|remind) (\S+) (.*)" add-memo)
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :.*" deliver-memos)

