(ns rekrvn.modules.quotes
  (:require [rekrvn.hub :as hub]
            [rekrvn.modules.mongo :as mongo]
            [clojure.string :as s]))

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
    (let [terms (s/split search-term #"\s+")
          regexs (map #(re-pattern (str "(?i)" %)) terms)
          re-maps (map (fn [r] {:text r}) regexs)]
      (mongo/get-rand-as-map modName {:channel chan '$and re-maps}))
    (mongo/get-rand-as-map modName {:channel chan})))

(defn quotes [[channel cmd line] reply]
  ;; connects to/disconnects from db, then
  ;; dispatches to get-quote, add-quote, or remove-quote
  (do
    (mongo/connect!)
    (case cmd
      "add" (when line
              (when-let
                [[_ nick text] (re-matches #"([a-zA-Z0-9_-]+):? (.+)" line)]
                (if (.getError (add-quote channel nick text))
                  (reply modName "Error adding quote.")
                  (reply modName "Quote added."))))
      "remove" (if (= 0 (.getN (remove-quote channel line)))
                 (reply modName "There are no quotes like that to remove.")
                 (reply modName "Quote removed."))

      ;; default: search for a quote
      (let [terms (when cmd (str cmd (when line (str " " line))))]
        (if-let [res (get-quote channel terms)]
          (reply modName (:text res))
          (reply modName (str "No quotes matching \"" terms "\" found.")))))
    (mongo/disconnect!)))

(hub/addListener modName #"^irc.*PRIVMSG #(\S+) :\.quote(?: (\S+)(?: (.+))?)?$" quotes)
