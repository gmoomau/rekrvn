(ns rekrvn.modules.caps
  (:require [rekrvn.hub :as hub]
            [rekrvn.modules.db :as db]))

(def modName "caps")

(defn caps [[channel line] reply]
  (if-let [[_ term] (re-matches #"(?:STOP|QUIT) (?:YELLING|SHOUTING) (.+)" line)]
    (if (= 0 (:next.jdbc/update-count (db/remove! modName {:channel channel :shout term})))
      (reply modName "I CAN'T!")
      (reply modName "FINE."))
    (when (> (count line) 3)
      (let [new-doc {:shout line :channel channel}
            finder {:channel channel}
            result (db/get-rand-as-map modName finder)]
        (db/insert! modName new-doc)
        (when result
          (reply modName (:shout result)))))))

(hub/addListener modName #"^irc.*PRIVMSG #(\S+) :([^a-z]*[A-Z]+[^a-z]*)$" caps)
