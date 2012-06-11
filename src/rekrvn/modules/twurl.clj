(ns rekrvn.modules.twurl
  (:require [rekrvn.hub :as hub])
  (:require [http.async.client :as c])
  (:use [cheshire.core])
  )

(defn expand-links [text urls]
  (reduce #(clojure.string/replace %1 (:url %2) (:expanded_url %2)) text urls))

(defn bold [text] (str (char 2) text (char 15)))
;; 0x02 bolds in irc and 0x0F (decimal 15) removes formatting

(defn niceify [tweet]
  (let [user-string (bold (:screen_name (:user tweet)))]
    (if (:retweeted_status tweet)
      (str user-string " RT @" (niceify (:retweeted_status tweet)))
      (str user-string " " (expand-links (:text tweet) (:urls (:entities tweet)))))))

(defn api-lookup [id]
  (with-open [client (c/create-client)]
    (let [url (str "http://api.twitter.com/1/statuses/show/" id ".json?include_entities=1")
          response (c/GET client url)]
      (c/await response)
      (c/string response))))

(defn twurl [[tweetid] reply]
  (when-let [jsn (api-lookup tweetid)]
    (let [parsed (parse-string jsn true)
          msg (niceify parsed)]
      (reply "twurl" msg))))

(hub/addListener "twurl" #"https?://\S*twitter\.com\S*/status(?:es)?/(\d+)" twurl)
