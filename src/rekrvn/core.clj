(ns rekrvn.core
  (:use rekrvn.irc))

(def servers [{:server "flounder.dyndns.org" :port 6998 :nick "mmic" :name "botty bots"
               :channels '("#test" "#urkl")}
              ;;{:server "flounder.dyndns.org" :port 6998 :nick "t2st" :name "mrs. bottersworth"}
              ])
(def doThese [{:id "mimic" :trigger #"^:\S+ PRIVMSG #?\w+ :(.*)" :f (fn [[matched] reply]
                                                                        (reply matched))}
               {:id "twurl" :trigger #"https?://.*twitter\.com.*/(.+)/status/(\d+)"
                :f (fn [[username tweet] reply] (reply (str username " " tweet)))}
               ])

(defn -main [& args]
  (pmap addTrigger doThese)

  ;; server connection must be the last line
  (map connect servers)
  )
