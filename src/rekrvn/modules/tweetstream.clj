(ns rekrvn.modules.tweetstream
  (:require [rekrvn.config :refer [twitter-stream-channel]]
            [rekrvn.hub :as hub]
            [rekrvn.modules.twitter :as util]
            [twitter.api.restful :refer :all]
            [clojure.tools.logging :as log]))

(def mod-name "tweetstream")
; docs https://developer.twitter.com/en/docs/tweets/timelines/api-reference/get-statuses-home_timeline .

(defn announce-tweet [tweet]
  (let [announcement (str mod-name " forirc " twitter-stream-channel " " (util/niceify tweet))]
    (log/info "printing tweet to" twitter-stream-channel ":" announcement)
    (hub/broadcast announcement))
  (when-let [quoted (:quoted_status tweet)]
    (hub/broadcast (str mod-name " forirc " twitter-stream-channel " " (util/niceify quoted)))))

(defn get-statuses [last-seen]
  (try
    (statuses-home-timeline
      :oauth-creds util/my-creds
      :params (merge {:include_entities true :tweet_mode "extended"} last-seen))
    (catch Exception e
      (log/error "tweetstream exception: " e)
      nil)))

;; TODO handle "Twitter responded to request with error 88: Rate limit exceeded. Next reset at 1589021843 (UTC epoch seconds)"
;; wait til then, log an error, alert somewhere

(defn stream []
  (loop [last-seen {:count 3}] ; the first time through there's no last seen, so just grab 3 tweets
    (log/info "last seen: " last-seen)
    (if-let [raw-statuses (get-statuses last-seen)]
      (let [tweets (:body raw-statuses)
            newest-seen (-> tweets first :id)
            new-last (if newest-seen {:since_id newest-seen} last-seen)]
        (log/info "first tweet: " (-> tweets first :text))
        (doseq [_ (reverse tweets)] (announce-tweet _))
        (Thread/sleep (* 70 1000)) ;; must be above 60sec. limit is 15req/15min
        (recur new-last))
      (do
        (Thread/sleep (* 300 1000)) ;; sleep 5min if there was an error
        (recur last-seen)))))

(doto (Thread. #(stream)) (.start))
