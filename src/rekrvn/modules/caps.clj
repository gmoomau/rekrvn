(ns rekrvn.modules.caps
  (:require [rekrvn.hub :as hub]
            [rekrvn.modules.mongo :as mongo]
            [clojure.tools.logging :as log]))

(def modName "caps")

(defn caps [[channel line] reply]
  (log/info "somebody shouted" line "in" channel)
  (if-let [[_ term] (re-matches #"(?:STOP|QUIT) (?:YELLING|SHOUTING) (.+)" line)]
    (do
      (log/info "deleting shout" term)
      (mongo/connect!)
      (if (= 0 (.getN (mongo/remove modName {:channel channel :text term})))
        (reply modName "I CAN'T!")
        (reply modName "FINE."))
      (mongo/disconnect!))
    (when (> (count line) 3)
      (do
        (log/info "saving shout" line)
        (mongo/connect!)
        (let [new-doc {:text line :channel channel}
              finder {:channel channel}
              result (mongo/get-rand-as-map modName finder)]
          (mongo/insert modName new-doc)
          (when result
            (reply modName (:text result)))
          (mongo/disconnect!))))))

(hub/addListener modName #"^irc.*PRIVMSG #(\S+) :([^a-z]*[A-Z]+[^a-z]*)$" caps)
