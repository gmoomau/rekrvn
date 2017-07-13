(ns rekrvn.modules.example
  (:require [rekrvn.hub :as hub]))

(defn mimic [[matched] reply]
  (reply "mimic" matched))

(hub/addListener "example" #"^irc.*PRIVMSG #\S+ :(.+)" mimic)

