(ns rekrvn.modules.twurl
  (:require [rekrvn.hub :as hub])
  (:use [cheshire.core])
  (:use [rekrvn.config :only [twitter-creds]])
  ;; consumer-key, consumer-token, user-token, user-secret
  (:use
    [twitter.oauth]
    [twitter.callbacks]
    [twitter.callbacks.handlers]
    [twitter.api.restful])
  (:import (twitter.callbacks.protocols AsyncSingleCallback)))

(def mod-name "twurl")

(def my-creds (make-oauth-creds (:consumer-key twitter-creds)
                                (:consumer-secret twitter-creds)
                                (:user-token twitter-creds)
                                (:user-secret twitter-creds)))

(defn expand-links [text urls]
  (reduce #(clojure.string/replace %1 (:url %2) (:expanded_url %2)) text urls))

(defn plaintext [text]
  (-> text
    (clojure.string/replace "&gt;" ">")
    (clojure.string/replace "&lt;" "<")
    (clojure.string/replace "&amp;" "&")))

(defn bold [text] (when text (str (char 2) text (char 15))))
;; 0x02 bolds in irc and 0x0F (decimal 15) removes formatting

(defn niceify [tweet]
  (when-let [user-string (bold (:screen_name (:user tweet)))]
    (str user-string " " (expand-links (plaintext (:text tweet)) (:urls (:entities tweet))))))

(defn get-tweet [id]
  (:body (statuses-show-id :oauth-creds my-creds
                           :params {:id id :include_entities true})))

(defn twurl [[tweetid] reply]
  (when-let [msg (niceify (get-tweet tweetid))]
    (reply mod-name msg)))

(hub/addListener mod-name #"https?://\S*twitter\.com\S*/status(?:es)?/(\d+)" twurl)
