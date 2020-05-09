(ns rekrvn.modules.twurl
  (:require [rekrvn.config :refer [twitter-creds]]
            [rekrvn.hub :as hub]
            [rekrvn.modules.twitter :as util]
            [twitter.api.restful :refer [statuses-show-id]]
            [twitter.oauth :refer :all]
            [clojure.tools.logging :as log]))

(def mod-name "twurl")
;; see docs at https://developer.twitter.com/en/docs/tweets/post-and-engage/api-reference/get-statuses-show-id .

(defn get-tweet [id]
  (try
    ;; throws an exception if the tweet is protected or deleted
    (:body (statuses-show-id :oauth-creds util/my-creds
                             :params {:id id :include_entities true}))
    (catch Exception e
      (log/error e "Couldn't get tweet" id))))

(defn twurl [[tweetid] reply]
  (let [tweet (get-tweet tweetid)]
    (if-let [msg (util/niceify (get-tweet tweetid))]
      (reply mod-name msg)
      (reply mod-name "Couldn't get tweet."))
    (when-let [quoted (:quoted_status tweet)]
      (reply mod-name (util/niceify quoted)))))

(hub/addListener mod-name #"https?://(?:\S*.)?twitter\.com\S*/status(?:es)?/(\d+)" twurl)
