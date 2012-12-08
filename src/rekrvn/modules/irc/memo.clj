(ns rekrvn.modules.irc.memo
  (:require [rekrvn.hub :as hub]))

(def modName "irc.memo")

(def memos (ref []))

(defn niceify [memo]
  (str (:recip memo) ", " (:sender memo) " says: " (:msg memo)))

(defn add-memo [[sender channel recip msg] reply]
  (if (= recip "cljr")
    (reply modName (str "suck it " sender))
    (dosync (alter memos conj {:sender sender :channel channel :recip recip :msg msg})
      (reply modName "memo'd"))))

(defn check-memos [[nick channel] reply]
  ""
  (dosync
    (doseq [memo @memos]
      (when (and (.equalsIgnoreCase nick (:recip memo)) (= channel (:channel memo)))
        (reply modName (niceify memo)))
    (ref-set memos (remove #(and (.equalsIgnoreCase nick (:recip %)) (= channel (:channel %))) @memos)))))

(hub/addListener modName #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.memo (\S+) (.*)" add-memo)
(hub/addListener modName #"^irc :(\S+)!\S+ PRIVMSG (\S+) :.*" check-memos)
