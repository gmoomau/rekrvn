(ns rekrvn.modules.irc.autoop
  (:require [rekrvn.hub :as hub]))

;; TODO: use settings in config file instead of hardcoding
;;       list of channel/network

(defn op [[nick channel] reply]
  (prn "autooping" nick channel)
  (hub/broadcast (str "irc.autoop irccmd synirc MODE " channel " +o " nick)))

(hub/addListener "irc.autoop" #"^irc :(\S+)!\S+ JOIN :(#hodradio)" op)
  ;;irc :grog!metellus@localhost JOIN :#test
