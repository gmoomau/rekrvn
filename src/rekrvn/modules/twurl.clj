(ns rekrvn.modules.twurl
  (:require [rekrvn.config :refer [twitter-creds]]
            [rekrvn.hub :as hub]
            [rekrvn.modules.twitter :as util]
            [twitter.api.restful :refer :all]
            [twitter.oauth :refer :all]))

(def mod-name "twurl")

(def my-creds (make-oauth-creds (:consumer-key twitter-creds)
                                (:consumer-secret twitter-creds)
                                (:user-token twitter-creds)
                                (:user-secret twitter-creds)))
(defn get-tweet [id]
  (try
    ;; throws an exception if the tweet is protected or deleted
    (:body (statuses-show-id :oauth-creds my-creds
                             :params {:id id :include_entities true :tweet_mode "extended"}))
    (catch Exception e (println (str "Caught exception: " (.getMessage e))) nil)))

(defn twurl [[tweetid] reply]
  (if-let [msg (util/niceify (get-tweet tweetid))]
    (reply mod-name msg)
    (reply mod-name "Couldn't get tweet.")))

(hub/addListener mod-name #"https?://(?:\S*.)?twitter\.com\S*/status(?:es)?/(\d+)" twurl)
