(ns rekrvn.modules.irc.emote
  (:require [rekrvn.hub :as hub])
  (:use [clojure.string :only [join]]))

(def modName "irc.emote")
(def faces {"shrug" "¯\\_(ツ)_/¯"
            "tdn"   "q-(ツ)-q"
            "tup"   "d-(ツ)-d"})

(defn say [[emote message] reply-fn]
  (when message
    (let [line (and message (-> message count inc (repeat "─") join))]
      (reply-fn modName (str "     ┌" line "┐"))
      (reply-fn modName (str "     │" message " │"))
      (reply-fn modName (str "     /" line "┘"))))
  (reply-fn modName (faces emote)))

(hub/addListener modName #"^irc.*PRIVMSG \S+ :\.(shrug|tdn|tup)( .+)?$" say)
