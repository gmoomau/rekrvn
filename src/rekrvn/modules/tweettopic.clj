(ns rekrvn.modules.tweettopic
  (:require [rekrvn.config :refer [tweet-topic-channel twitter-creds]]
            [rekrvn.hub :as hub]
            [rekrvn.modules.twitter :as util]
            [twitter.api.restful :refer :all]))

(def mod-name "tweettopic")
;; docs at https://developer.twitter.com/en/docs/tweets/post-and-engage/api-reference/post-statuses-update .

(defn tweet-topic [[channel text] & col]
  (if (= channel tweet-topic-channel)
    (let [truncated-text (if (> (.length text) 280) (str (subs text 0 275) "...") text)]
      (statuses-update :oauth-creds util/my-creds :params {:status truncated-text}))))

(hub/addListener mod-name #"irc \S+ TOPIC (\S+) :(.+\S.*)$" tweet-topic)

