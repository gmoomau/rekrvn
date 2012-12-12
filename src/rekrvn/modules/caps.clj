(ns rekrvn.modules.caps
  (:require [rekrvn.hub :as hub]
            [rekrvn.modules.mongo :as mongo]))

(def modName "caps")

(defn caps [[channel line] reply]
  (when (> (count line) 3)
    (do
      (mongo/connect!)
      (let [new-doc {:text line :channel channel}
            finder {:channel channel}
            result (mongo/get-rand-as-map modName finder)]
        (mongo/insert modName new-doc)
        (when result
          (reply modName (:text result)))
        (mongo/disconnect!)))))

(hub/addListener modName #"^irc.*PRIVMSG #(\S+) :([^a-z]*[A-Z]+[^a-z]*)$" caps)
