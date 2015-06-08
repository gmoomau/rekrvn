(ns rekrvn.modules.rimshot
  (:require [rekrvn.hub :as hub]))

(def modName "rimshot")

;(def rimShot "http://instantrimshot.com/")
(def rimShot "http://i.imgur.com/BbgL7x3.gif")
(def trombone "http://sadtrombone.com")
(def khan "http://www.khaaan.com/")
(def snowman "☃")

(hub/addListener modName #"^irc.*PRIVMSG \S+ :(\.(?:instant)?rimshot)$" (fn [_ refn] (refn modName rimShot)))
(hub/addListener modName #"^irc.*PRIVMSG \S+ :(\.(?:sad)?trombone)$" (fn [_ refn] (refn modName trombone)))
(hub/addListener modName #"(?i)^irc.*PRIVMSG \S+ :(\.kha+n)$" (fn [_ refn] (refn modName khan)))
(hub/addListener modName #"(?i)^irc.*PRIVMSG \S+ :(\.snowman)$" (fn [_ refn] (refn modName snowman)))

