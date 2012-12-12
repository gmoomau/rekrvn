(ns rekrvn.modules.quotes
  (:require [rekrvn.hub :as hub]
            [rekrvn.modules.mongo :as mongo]))

;; Usage:
;; .quote add <line>
;; .quote remove <line>
;; .quote ;; gives a random quote
;; .quote searchterm ;; gives a random quote matching the search term

(def modName "quotes")

(defn add-quote [chan nick text]
  (mongo/insert modName {:channel chan :text (str nick ": " text)}))

(defn remove-quote [chan text]
  (mongo/remove modName {:channel chan :text text}))

(defn get-quote [chan search-term]
  (if search-term
    (let [patt (re-pattern (str "(?i)" search-term))]
      ;; match should be case-insensitive
      (mongo/get-rand-as-map modName {:channel chan :text patt}))
    (mongo/get-rand-as-map modName {:channel chan})))

(defn quotes [[channel cmd line] reply]
  ;; connects to/disconnects from db, then
  ;; dispatches to get-quote, add-quote, or remove-quote
  (do
    (mongo/connect!)
    (case cmd
      "add" (when-let
              [[_ nick text] (re-matches #"([a-zA-Z0-9_-]+):? (.+)" line)]
              (add-quote channel nick text))
      "remove" (remove-quote channel line)

      ;; default: search for a quote
      (if-let [res (get-quote channel cmd)]
        (reply modName (:text res))
        (reply modName (str "No quotes matching \"" cmd "\" found."))))
    (mongo/disconnect!)))

(hub/addListener modName #"^irc.*PRIVMSG #(\S+) :\.quote(?: (\S+)(?: (.+))?)?$" quotes)
