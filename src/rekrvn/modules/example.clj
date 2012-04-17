(ns rekrvn.example
  (:require [rekrvn.core])
  )

(defn mimic [[matched] reply]
  (reply "mimic" matched))
(rekrvn.core/addListener "mimic" #"^irc.*PRIVMSG #\S+ :(.+)" mimic)

;(defn twurl [[username tweet] reply]
;  (reply "twurl" (str username " " tweet)))
;(rekrvn.core/addListener "twurl" #"^irc.*https?://.*twitter\.com.*/(.+)/status/(\d+)" twurl)

