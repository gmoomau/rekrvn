(ns rekrvn.modules.hoogle
  (:require [rekrvn.hub :as hub]))

(defn hoogle [[terms] reply]
  (reply "hoogle" "A hoogle error has occurred."))

(hub/addListener "hoogle" #"^irc.*PRIVMSG #\S+ :\.hoogle(\s.*)?$" hoogle)
