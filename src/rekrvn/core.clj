(ns rekrvn.core
  ;(:require [rekrvn.config])
  (:import (java.io File))
  )

(def baseDir "/home/grog/rekrvn/src/rekrvn")
(def modDir "modules/")
(def modCleanup (ref {})) ;; maps id->ending function
(def listeners (ref [])) ;; vec of triggers

(defn broadcast [{id :id content :content reply :replyFn}]
  (doall (map (fn [{matchFn :matcher actFn :action}]
                (let [results (matchFn id content)]
                  (when results (actFn results reply))))
              @listeners))
  )

(defn addCleanup [modId cleanFn]
  (dosync
    (alter modCleanup assoc modId cleanFn)
    )
  )

(defn addListener [matcher action]
  (dosync (alter listeners conj {:matcher matcher :action action}))
  )

(defn modLoad [modId]
  ;; remove existing mod if necessary
  (let [cleanup (get modCleanup modId)
        loneFile (str modDir modId)
        inDir (str modDir modId "/" modId)
        ]
    (when cleanup (cleanup)) ;; call cleanup if it exists
    (cond
      (.exists (File. baseDir (str loneFile ".clj"))) (load loneFile)
      (.exists (File. baseDir (str inDir ".clj"))) (load inDir)
      :else (println "No plugin with the name" modId "at" loneFile "or" inDir))
    )
  )


(defn -main [& args]
  (do
    ;;(println "broadcast 1")
    ;;(broadcast {:id "asd" :content "d" :replyFn (fn [] (+ 1 2))})
    (load "rekrvn/config")
    ;;(modLoad "blah")
    ;;(println "loaded")
    ;;(println "broadcast 2")
    (broadcast {:id "asd" :content ":g!mus@17.0.0.1 PRIVMSG #l :caught by mimic" :replyFn (fn [a] (println a))})
    (broadcast {:id "asd" :content "https://twitter.com/#!/twurlcatch/status/1972416" :replyFn (fn [a] (println a))})
    )
  )

