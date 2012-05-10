(ns rekrvn.modules.example
  (:require [rekrvn.hub :as hub])
  )

(defn mimic [[matched] reply]
  (reply "mimic" matched))
(hub/addListener "example" #"^irc.*PRIVMSG #\S+ :(.+)" mimic)

;(defn twurl [[username tweet] reply]
;  (reply "twurl" (str username " " tweet)))
;(hub/addListener #"^irc.*https?://.*twitter\.com.*/(.+)/status/(\d+)" twurl)

