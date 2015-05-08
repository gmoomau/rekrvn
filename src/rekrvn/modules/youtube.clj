(ns rekrvn.modules.youtube
  (:require [rekrvn.hub :as hub])
  (:use [rekrvn.config :only [google-key]])
  (:require [http.async.client :as c])
  (:use [http.async.client.request :only [url-encode]])
  (:require [net.cgrand.enlive-html :as h])
  (:require [clojure.string :as s])
  (:use [cheshire.core]))

(def mod-name "youtube")

(defn web-request [url]
  (with-open [client (c/create-client)]
    (let [response (c/GET client url)]
      (c/await response)
      (c/string response))))

(defn niceify [title dur]
  (when title
    (let [[_ hours minutes seconds] (re-matches #"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?" dur)
          duration (str "["
                        (when hours (str (format "%02d" (Integer/parseInt hours)) ":"))
                        (if minutes (format "%02d" (Integer/parseInt minutes)) "00") ":"
                        (if seconds (format "%02d" (Integer/parseInt seconds)) "00")
                        "]")]
      (str
        (char 3) "1,0You" (char 3) "0,5Tube" (char 15) (char 3) " "
        title " "
        duration))))
;; (char 15) added to work around a bug in Circ

(defn youtube [[vid] reply]
  (let [url (str "https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails&id=" vid "&key=" google-key)
        response (parse-string (web-request url) true)
        title (-> response :items first :snippet :title)
        duration (-> response :items first :contentDetails :duration)
        msg (niceify title duration)]
    (reply mod-name msg)))

(hub/addListener "youtube" #"(?:youtu\.be/|youtube\.com/(?:watch\?\S*v=|embed/))([A-Za-z0-9_-]+)" youtube)
