(ns rekrvn.modules.weather
  (:require [rekrvn.hub :as hub])
  (:require [http.async.client :as c])
  (:use [http.async.client.request :only [url-encode]])
  )

(def mod-name "weather")

(defn niceify [raw-weather]
  (let [regx #".+\|(\d+)\|.+\|.+\|(.+)\|.+\|.+\|(.+)\|(.+)\|.+\|.+\|.+\|.+\|.+\|.+\|.+\|.+\|.+\|(.+)\|(.+)\|(\d\d:\d\d \S+) \((.+)\)\|.+\|.+\|.+\|"
        [temp humidity pressure sky sloc bloc clock zone] (rest (re-find regx raw-weather))]
    (str sloc ", " bloc " (" clock " " zone "): " temp "°F - " sky " | "
         humidity " Humidity | Barometer: " pressure)))


(defn api-lookup [location]
  (with-open [client (c/create-client)]
    (let [url (str "http://www.wunderground.com/auto/raw/" (url-encode location))
          response (c/GET client url)]
      (c/await response)
      (c/string response))))

(defn weather [[location] reply]
  (if-let [raw-weather (api-lookup location)]
    (reply mod-name (niceify raw-weather))
    (reply mod-name (str "Could not find weather for " location))))

(hub/addListener "weather" #"^irc.*PRIVMSG \S+ :\.w (.+)$" weather)
