(ns rekrvn.modules.util)

(defn plaintext [text]
  (-> text
    (clojure.string/replace "&gt;" ">")
    (clojure.string/replace "&lt;" "<")
    (clojure.string/replace "&amp;" "&")
    (clojure.string/replace "\n" "   ")))

(defn bold [text] (when text (str (char 2) text (char 15) )))
;; 0x02 bolds in irc and 0x0F (decimal 15) removes formatting
;; currently not used
