;; WARNING: You probably shouldn't use .load on things with long-
;; lived connections, like irc or twitter
(ns rekrvn.modcontrols
  (:require rekrvn.core))

(rekrvn.core/addListener "modcontrols"
  #"^irc.*PRIVMSG \S+ :\.load (\S+)$"
  (fn [[modName] reFn] (rekrvn.core/modLoad modName)))
