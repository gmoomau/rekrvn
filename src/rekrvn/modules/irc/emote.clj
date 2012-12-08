(ns rekrvn.modules.irc.emote
  (:require [rekrvn.hub :as hub]))

(def modName "irc.emote")
(def shrug "¯\\_(ツ)_/¯")
(def tup "d_(ツ)_d")
(def tdn "q-(ツ)-q")

(defn bubble [face message refn]
  "speech bubbles if appropriate"
  (if message
    (do
      (let [width (+ 2 (.length message))
            line (apply str (repeat width "─"))]
        (refn modName (apply str "     ┌" line "┐"))
        (refn modName (apply str "     │ " message " │"))
        (refn modName (apply str "     /" line "┘"))
        (refn modName face)))
    (refn modName face)))

(hub/addListener modName #"^irc.*PRIVMSG \S+ :\.shrug(?: )?(.+)?" (fn [[msg] refn] (bubble shrug msg refn)))
(hub/addListener modName #"^irc.*PRIVMSG \S+ :\.tup(?: )?(.+)?" (fn [[msg] refn] (bubble tup msg refn)))
(hub/addListener modName #"^irc.*PRIVMSG \S+ :\.tdn(?: )?(.+)?" (fn [[msg] refn] (bubble tdn msg refn)))
