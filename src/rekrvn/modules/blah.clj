(ns rekrvn.blah
  (:require [rekrvn.core])
  )

(defn mimicmatch [msgtype content]
  (when (.equals msgtype "irc")
    (let [res (re-find #"PRIVMSG #\S+ :(.+)" content)]
      (if res (rest res) nil))))
(defn mimic [[matched] reply]
  (reply matched))
(rekrvn.core/addListener #"^irc.*PRIVMSG #\S+ :(.+)" mimic)

(defn twurlmatch [a b]
  (when (.equals a "irc")
    (let [res (re-find #"https?://.*twitter\.com.*/(.+)/status/(\d+)" b)]
      (if res (rest res) nil))))
(defn twurl [[username tweet] reply]
  (reply (str username " " tweet)))
(rekrvn.core/addListener #"^irc.*https?://.*twitter\.com.*/(.+)/status/(\d+)" twurl)

