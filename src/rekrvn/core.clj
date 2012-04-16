(ns rekrvn.core
  ;(:require [rekrvn.config])
  (:import (java.io File))
  )

(def baseDir "/home/grog/rekrvn/src")
(def modDir "rekrvn/modules/")
(def modCleanup (ref {})) ;; maps id->ending function
(def listeners (ref [])) ;; vec of triggers

;; string content fn reply
(defn broadcast [content reply]
  (doall (map (fn [{matcher :matcher actFn :action}]
                (when-let [results (re-find matcher content)]
                  (actFn (rest results) reply)))
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

(defn initMods []
  (let [mods (load-file (str baseDir "/rekrvn/config.clj"))]
    (doall (map
             (fn [module] (modLoad module))
             mods))
    )
  )


(defn -main [& args]
  ;;(do
  ;;  (load "rekrvn/config")
  ;;  (let [tmp (load-file (str baseDir "tmp.clj"))]
  ;;    (println "tmp is" tmp))
  ;;  )
  (initMods)
  (println "dir is " (.getCanonicalPath (File. ".")))
  )

