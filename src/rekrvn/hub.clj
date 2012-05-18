(ns rekrvn.hub
  (:require [rekrvn.config])
  )

(def listeners (ref [])) ;; list of triggers

;; string content fn reply
(defn broadcast
  ([content] (broadcast content (fn [& args])))
  ([content reply]
  (doseq [{matcher :matcher actFn :action} @listeners]
    (doseq [results (re-seq matcher content)]
      (actFn (rest results) reply)))))

(defn addListener [modname matcher action]
  (dosync (alter listeners conj {:mod modname :matcher matcher :action action}))
  )

(defn modload [modId]
  (let [module (symbol (str "rekrvn.modules." modId))]
    (try
      (require module :reload)
    (catch Exception e (str "Caught exception: " (.getMessage e)))))
  )

(defn reload [modId]
  (do
    (dosync (ref-set listeners (remove (fn [trig] (= modId (:mod trig))) @listeners)))
    (modload modId))
  )

(defn initMods []
  (doseq [modName rekrvn.config/modules] (modload modName)))


(defn -main [& args]
  (initMods)
  )
