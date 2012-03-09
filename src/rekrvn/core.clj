(ns rekrvn.core
  (:use rekrvn.irc))

(def servers [{:server "flounder.dyndns.org" :port 6998 :nick "mimk" :name "kruvina gloster"}
              ;;{:server "flounder.dyndns.org" :port 6998 :nick "t2st" :name "mrs. bottersworth"}
              ])

(defn -main [& args]
  (addTrigger "mimic" #"^:\S+ PRIVMSG #?\w+ :(.*)" (fn [[matched] reply]
                                                     (reply matched)))
  (addTrigger "twitter"
              #"https?://.*twitter\.com.*/(.+)/status/(\d+)" (fn [[username tweet] reply]
                                                                  (reply (str username " " tweet))))

  ;; server connection must be the last line
  (map connect servers)
  )
