(ns rekrvn.modules.twitter
  (:require [clojure.tools.logging :as log]
            [rekrvn.config :refer [twitter-creds]]
            [twitter.oauth :refer [make-oauth-creds]]))

;(def my-creds (make-oauth-creds (:consumer-key twitter-creds)
;                                (:consumer-secret twitter-creds)
;                                (:user-token twitter-creds)
;                                (:user-secret twitter-creds)))
(def my-creds
  (let [grab-info (juxt :consumer-key :consumer-secret :user-token :user-secret)]
    (apply make-oauth-creds (grab-info twitter-creds))))

;;;;;;;;;;;;;; tweet formatting stuff below this point ;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- expand-single-link [text short-url long-url]
  (if long-url
    (clojure.string/replace text short-url long-url)
    text))

(defn expand-links [text urls]
  (reduce #(expand-single-link %1 (:url %2) (:expanded_url %2)) text urls))

(defn plaintext [text]
  (-> text
    (clojure.string/replace "&gt;" ">")
    (clojure.string/replace "&lt;" "<")
    (clojure.string/replace "&amp;" "&")
    (clojure.string/replace "\n" "   ")))

(defn bold [text] (when text (str (char 2) text (char 15) )))
;; 0x02 bolds in irc and 0x0F (decimal 15) removes formatting
;; currently not used

(defn color [text] (when text (str (char 3) "11" text (char 15) (char 3))))
;; 0x03 is color and 11 is cyan
;; (char 15) clears formatting. added to work around a bug in Circ

(defn full-tweet-text [tweet]
  ; twitter truncates some tweets. undo that.
  (cond
    (:extended_tweet tweet)     (-> tweet :extended_tweet :full_text)
    (:retweeted_status tweet)   (when-let [rtd_name
                                           (re-find #"RT @[^:]+: "
                                                    (or (:text tweet) (:full_text tweet)))]
                                  (str rtd_name (full-tweet-text (:retweeted_status tweet))))
    (:full_text tweet)          (:full_text tweet)
    :else                       (:text tweet)))

(defn niceify [tweet]
  (when tweet
    (when-let [user-string (color (str "@" (:screen_name (:user tweet))))]
      (str user-string " "
           (expand-links (plaintext (full-tweet-text tweet)) (:urls (:entities tweet)))))))
           ;(when-let [quoted (niceify (:quoted_status tweet))]
           ;  (str " " (char 3) "14[" (char 3) quoted " " (char 3) "14]" (char 3)))))))

