(ns rekrvn.modules.tweetstream
  (:require [rekrvn.hub :as hub])
  (:use [cheshire.core])
  (:use [rekrvn.config :only [twitter-creds twitter-stream-channel]])
  (:require [rekrvn.modules.twitter :as util])
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

(declare open-user-stream)
(def wait 30000)

;; TODO: multi-channel support
(defn announce-tweet [tweet]
  (let [announcement (str mod-name " forirc " twitter-stream-channel " " (util/niceify tweet))]
    (println "announcing tweet: " announcement)
    (hub/broadcast announcement)))

;; temporary, get rid of this or something. (replace with proper logging?)
(defn announce-error [message]
  (let [error (str mod-name " forirc faceroar##dump " message)]
    (println "announcing error: " error)
    (hub/broadcast error)))

(defn json-to-tweet [tweet-string]
  (try
    (parse-string tweet-string true)
    (catch Exception e
      (announce-error "partial tweet happened"))))

;;;;;;;;;;; a chain of functions for thread stuff
(defn handle-part [response json-bytes]
  ;; TODO: stop being terrible and aggregate chunks
  ;; TODO: distinguish between different types of messages from twitter
  (let [json (str json-bytes)]
    (when (not (blank? json))
      (println "from" mod-name json)
      (announce-error "got something")
      (when-let [tweet (json-to-tweet json)]
        (announce-tweet tweet)))))

(defn handle-failure [response]
  (announce-error "twitter got mad"))
;  (do
;    (announce-error "twitter got mad")
;    (Thread/sleep wait)
;    (open-user-stream)))

(defn handle-exception [response throwable]
  (announce-error (str "exception " throwable)))
;  (do
;    (announce-error (str "exception " throwable))
;    (println throwable)
;    (Thread/sleep wait)
;    (open-user-stream)))

;; twitter response callback
(def ^:dynamic *custom-streaming-callback*
  (AsyncStreamingCallback. ;; on-bodypart, on-failure, on-exception
    handle-part
    handle-failure
    handle-exception))

(defn open-user-stream []
  (user-stream :oauth-creds my-creds :callbacks *custom-streaming-callback*))

(open-user-stream)
