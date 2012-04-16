(ns rekrvn.core
  ;(:require [rekrvn.config])
  (:import (java.io File))
  )

(def baseDir "/home/grog/rekrvn/src")
(def modDir "rekrvn/modules/")
(def modCleanup (ref {})) ;; maps id->ending function
(def listeners (ref {})) ;; maps id->trigger

;; string content fn reply
(defn broadcast [content reply]
  (doall (map (fn [{matcher :matcher actFn :action}]
                (when-let [results (re-find matcher content)]
                  (actFn (rest results) reply)))
              (vals @listeners)))
  )

(defn addListener [id matcher action]
  (dosync (alter listeners assoc id {:matcher matcher :action action}))
  )

(defn modLoad [modId]
  ;; remove existing mod if necessary
  (let [loneFile (str baseDir "/" modDir modId ".clj")
        inDir (str baseDir "/" modDir modId "/" modId ".clj")
        ]
    (cond
      (.exists (File. loneFile)) (load-file loneFile)
      (.exists (File. inDir)) (load-file inDir)
      :else (println "No plugin with the name" modId "at" loneFile "or" inDir))
    )
  )

(defn initMods []
  (let [mods (load-file (str baseDir "/rekrvn/config.clj"))]
    (doall (map modLoad mods))
    )
  )


(defn -main [& args]
  ;;(do
  ;;  (load "rekrvn/config")
  ;;  (let [tmp (load-file (str baseDir "tmp.clj"))]
  ;;    (println "tmp is" tmp))
  ;;  )
  (initMods)
  )
