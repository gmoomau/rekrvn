(ns rekrvn.hub
  (:require [rekrvn.config])
  (:require [clojure.tools.logging :as log]))

(def listeners (ref [])) ;; list of triggers

;; string content fn reply
(defn broadcast
  ([content] (broadcast content (fn [& args])))
  ([content reply] (broadcast content reply (fn [_] true)))
  ([content reply filter-fn]
   (doseq [{matcher :matcher act-fn :action mod-name :mod} @listeners]
     (when (filter-fn mod-name)
       (doseq [results (re-seq matcher content)]
         (log/debug "running module" mod-name "with arguments" (rest results))
         (try
           (act-fn (rest results) reply)
           (catch Exception e
             (log/error e "exception running" mod-name))))))))

(defn addListener [modname matcher action]
  (log/info "adding listener for" modname)
  (dosync (alter listeners conj {:mod modname :matcher matcher :action action})))

(defn modload [modId]
  (let [module (symbol (str "rekrvn.modules." modId))]
    (log/info "loading module" modId)
    (try
      (require module :reload)
      (catch Exception e (log/error e "exception while loading module" modId)))))

(defn reload [modId]
  (do
    (dosync (ref-set listeners (remove (fn [trigger] (= modId (:mod trigger))) @listeners)))
    (require 'rekrvn.config :reload)
    (modload modId)))

(defn initMods []
  (doseq [modName rekrvn.config/modules] (modload modName)))

(defn -main [& args]
  (initMods))
