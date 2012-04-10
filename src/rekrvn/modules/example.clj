(ns rekrvn.blah
  (:require [rekrvn.core])
  )

(defn mimic [[matched] reply]
  (reply matched))
(rekrvn.core/addListener #"^irc.*PRIVMSG #\S+ :(.+)" mimic)

(defn twurl [[username tweet] reply]
  (reply (str username " " tweet)))
(rekrvn.core/addListener #"^irc.*https?://.*twitter\.com.*/(.+)/status/(\d+)" twurl)

