(ns rekrvn.modules.twitter)
;; tweet formatter

(defn expand-links [text urls]
  (reduce #(clojure.string/replace %1 (:url %2) (:expanded_url %2)) text urls))

(defn plaintext [text]
  (-> text
    (clojure.string/replace "&gt;" ">")
    (clojure.string/replace "&lt;" "<")
    (clojure.string/replace "&amp;" "&")
    (clojure.string/replace "\n" " ")))

(defn bold [text] (when text (str (char 2) text (char 15))))
;; 0x02 bolds in irc and 0x0F (decimal 15) removes formatting

(defn niceify [tweet]
  (when-let [user-string (bold (:screen_name (:user tweet)))]
    (str user-string " " (expand-links (plaintext (:text tweet)) (:urls (:entities tweet))))))

