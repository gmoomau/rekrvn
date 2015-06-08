(ns rekrvn.modules.twurl
  (:require [rekrvn.hub :as hub])
  (:require [rekrvn.modules.twitter :as util])
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
(defn get-tweet [id]
  (try
    ;; throws an exception if the tweet is protected or deleted
    (:body (statuses-show-id :oauth-creds my-creds
                             :params {:id id :include_entities true}))
    (catch Exception e (println (str "Caught exception: " (.getMessage e))) nil)))

(defn twurl [[tweetid] reply]
  (if-let [msg (util/niceify (get-tweet tweetid))]
    (reply mod-name msg)
    (reply mod-name "Couldn't get tweet.")))

(hub/addListener mod-name #"https?://\S*twitter\.com\S*/status(?:es)?/(\d+)" twurl)
