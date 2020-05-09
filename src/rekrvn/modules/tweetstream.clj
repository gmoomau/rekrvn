(ns rekrvn.modules.tweetstream
  (:require [rekrvn.config :refer [twitter-creds
                                   twitter-stream-channel]]
            [rekrvn.hub :as hub]
            [rekrvn.modules.twitter :as util]
            [twitter.api.restful :refer :all]
            [twitter.oauth :as oauth]
            [clojure.tools.logging :as log]))

(def mod-name "tweetstream")
;; WIP

(defn announce-tweet [tweet]
  (let [announcement (str mod-name " forirc " twitter-stream-channel " " (util/niceify tweet))]
    (log/info "printing tweet to" twitter-stream-channel ":" announcement)
    (hub/broadcast announcement))
  (when-let [quoted (:quoted_status tweet)]
    (hub/broadcast (str mod-name " forirc " twitter-stream-channel " " (util/niceify quoted)))))
