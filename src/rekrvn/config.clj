(ns rekrvn.config
  (:require rekrvn.core))

(def modules ["irc" "blah"])





;;;;;;;;;;; do not touch below this line ;;;;;;;
(doall (map
         (fn [module] (rekrvn.core/modLoad module))
         modules))


