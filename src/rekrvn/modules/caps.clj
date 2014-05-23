(ns rekrvn.modules.caps
  (:require [rekrvn.hub :as hub]
            [rekrvn.modules.mongo :as mongo]))

(def modName "caps")

(defn isRealShout? [line]
  (or 
    (re-matches #".+!" line)
    (> (count ((apply str (re-seq #"[A-Z]+" line)))) 4)))

(defn caps [[channel line] reply]
  (if-let [[_ term] (re-matches #"(?:STOP|QUIT) (?:YELLING|SHOUTING) (.+)" line)]
    (do
      (mongo/connect!)
      (if (= 0 (.getN (mongo/remove modName {:channel channel :text term})))
        (reply modName "I CAN'T!")
        (reply modName "FINE."))
      (mongo/disconnect!))
    (when (isRealShout? line)
      (do
        (mongo/connect!)
        (let [new-doc {:text line :channel channel}
              finder {:channel channel}
              result (mongo/get-rand-as-map modName finder)]
          (mongo/insert modName new-doc)
          (when result
            (reply modName (:text result)))
          (mongo/disconnect!))))))

(hub/addListener modName #"^irc.*PRIVMSG #(\S+) :([^a-z]*[A-Z]+[^a-z]*)$" caps)
