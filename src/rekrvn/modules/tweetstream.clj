(ns rekrvn.modules.tweetstream
  (:require [rekrvn.hub :as hub])
  (:use [cheshire.core])
  (:use [rekrvn.config :only [twitter-creds twitter-stream-channel]])
  (:use [clojure.string :only [blank?]]
        [twitter.oauth]
        [twitter.callbacks]
        [twitter.callbacks.handlers]
        [twitter.api.streaming])
  (:require [http.async.client :as ac])
  (:import (twitter.callbacks.protocols AsyncStreamingCallback)
           (java.lang Thread)))

(def mod-name "tweetstream")

(def my-creds (make-oauth-creds (:consumer-key twitter-creds)
                                (:consumer-secret twitter-creds)
                                (:user-token twitter-creds)
                                (:user-secret twitter-creds)))

;;;;;;;;;;; functions that should be refactored out
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

(defn announce-tweet [tweet]
  (let [announcement (str mod-name " forirc " twitter-stream-channel  " " (niceify tweet))]
    (println announcement)
    (hub/broadcast announcement)))


;;;;;;;;;;; a chain of functions for thread stuff
(defn handle-stream-component [stream response]
  (let [string-response (str response)]
    (when (not (blank? string-response))
      (println "fromtweetstream" string-response)
      (let [response-map (parse-string string-response true)]
        (when (:text response-map)
          (announce-tweet response-map))))))

; supply a callback that only prints the text of the status
(def ^:dynamic *custom-streaming-callback*
  (AsyncStreamingCallback.
    handle-stream-component
    (comp println response-return-everything)
    exception-print))

(defn open-user-stream []
  (user-stream :oauth-creds my-creds :callbacks *custom-streaming-callback*))

(defn start-getting-stream [pause]
  ;; pause is for exponential delay on retries if i ever bother to implement that
  (let [^:dynamic *response* (open-user-stream)]
    (do
      (Thread/sleep 60000); sleep for a minute, then cancel the async call
      (:cancel (meta *response*)))))

;;;;;;;;;;; this kicks off stuff in a thread or whatever
(doto (Thread. #(start-getting-stream 1)) (.start))
