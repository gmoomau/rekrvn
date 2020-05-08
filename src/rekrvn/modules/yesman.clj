(ns rekrvn.modules.yesman
  (:require [rekrvn.hub :as hub]))

(def mod-name "yesman")
;; backs you up on that. sometimes.

(defn yep [[chain] reply]
  (let [len (inc (count chain))]
    ;; 50% chance of saying ^^, 25% for ^^^^, etc
    (when (< (rand) (/ 1 len))
      (reply mod-name (apply str (repeat len "^")))))) 

(hub/addListener mod-name #"^irc.*PRIVMSG \S+ :(\^+)" yep)
