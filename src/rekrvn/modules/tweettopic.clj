(ns rekrvn.modules.tweettopic
  (:require [rekrvn.config :refer [twitter-creds]]
            [rekrvn.hub :as hub]
            [twitter.api.restful :refer :all]
            [twitter.oauth :refer :all]))

(def mod-name "tweettopic")

(def my-creds (make-oauth-creds (:consumer-key twitter-creds)
                                (:consumer-secret twitter-creds)
                                (:user-token twitter-creds)
                                (:user-secret twitter-creds)))

(defn tweet-topic [[channel text] & col]
  (let [truncated-text (if (> (.length text) 140) (str (subs text 0 135) "...") text)]
    (if (= channel "#room")
      (statuses-update :oauth-creds my-creds
                       :params {:status truncated-text}))))

(hub/addListener mod-name #"irc \S+ TOPIC (\S+) :(.+\S.*)$" tweet-topic)

