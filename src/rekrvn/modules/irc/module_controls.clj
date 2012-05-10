;; WARNING: You probably shouldn't use .load on things with long-
;; lived connections, like irc or twitter
(ns rekrvn.irc.modcontrols
  (:require [rekrvn.hub :as hub]))

(hub/addListener
  "irc.modcontrols"
  #"^irc.*PRIVMSG \S+ :\.load (\S+)$"
  (fn [[modName] reFn] (hub/reload modName)))
