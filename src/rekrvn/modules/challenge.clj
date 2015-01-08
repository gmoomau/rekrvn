(ns rekrvn.modules.challenge
  (:require [rekrvn.hub :as hub]
            ))

;; Usage:
;; .chal ;; presents current challenge
;; .chal set <line> ;; sets the challenge to the given statement
;; .chal accept <line> ;; stores user/line combo as response to most recently presented challenge
;; .chal get   ;; returns random response for most recent challenge

(def modName "challenge")
(def challenge nil)
(def contenders (atom []))

(defn get-challenge []
  challenge
)

(defn set-challenge [text]
  (def contenders (atom []))
  (def challenge text)
)

(defn accept-challenge [nick text]
  (if (nil? challenge) nil
     ;; update the mapping
     (swap! contenders conj (list nick text))
  )
)

(defn get-contender []
  (@contenders 
    (rand-int (count @contenders))
  )
)

(defn challenges [[channel cmd line] reply]
    (case cmd
      "set" (do (set-challenge) (reply modName challenge))
 
      "accept" (when line
                 (when-let
                   [[_ nick text] (re-matches #"([a-zA-Z0-9_-]+):? (.+)" line)]
                 (do (accept-challenge nick text)
                     (reply modName "Accepted!"))))

      "get" (get-contender)

      ;; default: return current challenge
      (get-challenge))
 )

(hub/addListener modName #"^irc.*PRIVMSG #(\S+) :\.chal(?: (\S+)(?: (.+))?)?\s*$" challenges)
