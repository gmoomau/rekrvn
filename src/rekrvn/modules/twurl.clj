(ns rekrvn.modules.twurl
  (:require [rekrvn.hub :as hub])
  (:require [http.async.client :as c])
  (:use [cheshire.core])
  )

(defn niceify [tweet]
  ;; 0x02 bolds in irc and 0x0F (decimal 15) removes formatting
  (str (char 2) (:screen_name (:user tweet)) (char 15) " " (:text tweet)))

(defn apiLookup [id]
  (with-open [client (c/create-client)]
    (let [url (str "http://api.twitter.com/1/statuses/show/" id ".json")
          response (c/GET client url)]
      ;; wait for response to be received
      (c/await response)
      ;; read body of response as string
      (c/string response)
  )))

(defn twurl [[tweetid] reply]
  (when-let [jsn (apiLookup tweetid)]
    (let [parsed (parse-string jsn true)
          msg (niceify parsed)]
      (when reply (reply "twurl" msg))
      )))

(hub/addListener "twurl" #"https?://\S*twitter\.com\S*/status/(\d+)" twurl)
