(ns rekrvn.modules.tweetstream
  (:require [rekrvn.config :refer [twitter-creds
                                   twitter-stream-channel]]
            [rekrvn.hub :as hub]
            [rekrvn.modules.twitter :as util]
            [twitter-streaming-client.core :as twclient]
            [twitter.oauth :as oauth]
            [clojure.tools.logging :as log]))

(def mod-name "tweetstream")

(def my-creds (oauth/make-oauth-creds (:consumer-key twitter-creds)
                                (:consumer-secret twitter-creds)
                                (:user-token twitter-creds)
                                (:user-secret twitter-creds)))

;; TODO: multi-channel support
(defn announce-tweet [tweet]
  (let [announcement (str mod-name " forirc " twitter-stream-channel " " (util/niceify tweet))]
    (log/info "printing tweet to" twitter-stream-channel ":" announcement)
    (hub/broadcast announcement))
  (when-let [quoted (:quoted_status tweet)]
    (hub/broadcast (str mod-name " forirc " twitter-stream-channel " " (util/niceify quoted)))))

(def stream (twclient/create-twitter-stream twitter.api.streaming/user-stream
                                           :oauth-creds my-creds))

(defn handle-tweets [stream]
  (while true
    (doseq [tweet (:tweet (twclient/retrieve-queues stream))]
      (log/info "got tweet:" tweet)
      (announce-tweet tweet))
    (Thread/sleep 1000)))

(twclient/start-twitter-stream stream)
(doto (Thread. #(handle-tweets stream)) (.start))
