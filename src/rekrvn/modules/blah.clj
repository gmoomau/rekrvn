(ns rekrvn.blah
  (:require [rekrvn.core])
  )

(defn mimicmatch [msgtype content]
  (let [res (re-find #"PRIVMSG #\S+ :(.+)" content)]
    (if res (rest res) nil)))
(defn mimic [[matched] reply]
  (reply matched))
(rekrvn.core/addListener mimicmatch mimic)

(defn twurlmatch [a b] (let [res (re-find #"https?://.*twitter\.com.*/(.+)/status/(\d+)" b)]
                         (if res (rest res) nil)))
(defn twurl [[username tweet] reply]
  (reply (str username " " tweet)))
(rekrvn.core/addListener twurlmatch twurl)

