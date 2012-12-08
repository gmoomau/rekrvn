(ns rekrvn.modules.rimshot
  (:require [rekrvn.hub :as hub]))

(def modName "rimshot")

(def rimShot "http://instantrimshot.com/")
(def trombone "http://sadtrombone.com")
(def khan "http://www.khaaan.com/")

(hub/addListener modName #"^irc.*PRIVMSG \S+ :(\.(?:instant)?rimshot)$" (fn [_ refn] (refn modName rimShot)))
(hub/addListener modName #"^irc.*PRIVMSG \S+ :(\.(?:sad)?trombone)$" (fn [_ refn] (refn modName trombone)))
(hub/addListener modName #"^irc.*PRIVMSG \S+ :(\.kha+n)$" (fn [_ refn] (refn modName khan)))

