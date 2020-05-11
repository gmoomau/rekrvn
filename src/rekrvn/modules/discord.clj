(ns rekrvn.modules.discord
  (:require [rekrvn.config :refer [discord-bot]]
            [rekrvn.hub :as hub]))

(def mod-name "discord")
;; integration with discord irc bot. re-broadcasts messages from the bot
;; as if they were made by the discord user. causes some modules (ex. youtube)
;; to trigger twice

(def msg-from-discord (re-pattern (str
                       "^irc :"
                       discord-bot
                       "!\\S+ PRIVMSG (\\S+) "
                       ":\\S*<.?\\d+(\\D\\S*).\\d*"
                       ">\\S* (.+)$")))

;; irc :krvn!krvn@sup-blah PRIVMSG #room :<11sgtw> stuff goes here

(defn relay [[where who what] reply]
  (let [re-wrap (fn [original-mod content] (reply mod-name content))]
    (hub/broadcast (str "irc :" who "!junk PRIVMSG " where " :" what) re-wrap)))

;; TODO: catch last link so bare .pls will work

(hub/addListener mod-name msg-from-discord relay)
