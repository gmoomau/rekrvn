(ns rekrvn.modules.quotes
  (:require [rekrvn.hub :as hub]
            [rekrvn.modules.db :as db]))

;; Usage:
;; .quote add <line>
;; .quote remove <line>
;; .quote ;; gives a random quote
;; .quote searchterm ;; gives a random quote matching the search term

(def mod-name "quotes")

(defn add-quote [chan nick text]
  (db/insert! mod-name {:channel chan :quote (str nick ": " text)}))

(defn remove-quote [chan text]
  (db/remove! mod-name {:channel chan :quote text}))

(defn get-quote [chan search-term]
  (if search-term
    (let [terms (clojure.string/split search-term #"\s+")
          terms-strs (map (fn [t] (str "%" t "%")) terms)
          search-clause (clojure.string/join " and " (repeat (count terms) "quote like ?"))
          chan-str (str "channel is \"" chan \"" and")
          full-terms (into [(str chan-str " " search-clause)] terms-strs)]
      (db/get-rand-as-map mod-name (into [(str chan-str " " search-clause)] terms-strs)))
    (db/get-rand-as-map mod-name {:channel chan})))

(defn quotes [[channel cmd line] reply]
  ;; dispatches to get-quote, add-quote, or remove-quote
  (do
    (case cmd
      "add" (when-let [[_ nick text] (and line (re-matches #"([a-zA-Z0-9_-]+):? (.+)" line))]
              (reply (add-quote channel nick text)))
      ;  (reply mod-name "Quote added.")
      "remove" (if (= 0 (:next.jdbc/update-count (remove-quote channel line)))
                 (reply mod-name "There are no quotes like that to remove.")
                 (reply mod-name "Quote removed."))

      ;; default: search for a quote
      (let [terms (when cmd (str cmd (when line (str " " line))))
            res (get-quote channel terms)]
        (if (empty? res)
          (reply mod-name (str "No quotes matching \"" terms "\" found."))
          (reply mod-name (:quote res)))))))

(hub/addListener mod-name #"^irc.*PRIVMSG #(\S+) :\.quote(?: (\S+)(?: (.+))?)?\s*$" quotes)
