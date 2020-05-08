(ns rekrvn.modules.manual
  (:require [rekrvn.hub :as hub]))

(def mod-name "manual")
;; run all modules against a specific line of chat.
;; useful for modules that you don't always want active, but still want
;; to easily trigger

(defn relay [[who where what] reply]
  (let [re-wrap (fn [original-mod content] (reply mod-name content))]
    (hub/broadcast (str "irc :" who "!junk PRIVMSG " where " :" what) re-wrap)))

;; TODO: catch last link so bare .pls will work

(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.pls (.+)$" relay)
