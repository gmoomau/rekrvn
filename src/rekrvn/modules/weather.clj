(ns rekrvn.modules.weather
  (:require [rekrvn.hub :as hub]
            [rekrvn.modules.mongo :as mongo]
            [http.async.client :as c]
            [clojure.string :as s])
  (:use [http.async.client.request :only [url-encode]]))

(def mod-name "weather")

(defn niceify [raw-weather]
  (let [regx #"(\d?\d:\d\d \S+ \S+).+\|(-?\d+)\|[^|]+\|[^|]+\|(\d+%)\|[^|]+\|[^|]+\|(\d+\.\d+)\|([^|]+)\|[^|]+\|[^|]+\|[^|]+\|[^|]+\|[^|]+\|[^|]+\|[^|]+\|[^|]+\|[^|]+\|([^|]+)\|([^|]+)\|[^|]+\|[^|]+\|[^|]+\|(?:[^|]+|)"
        [clock temp humidity pressure sky sloc bloc] (rest (re-find regx raw-weather))]
    (str sloc ", " bloc " (" clock "): " temp "°F - " sky " | "
         humidity " Humidity | Barometer: " pressure)))


(defn api-lookup [location]
  (with-open [client (c/create-client)]
    (let [url (str "http://www.wunderground.com/auto/raw/" (url-encode location))
          response (c/GET client url)]
      (c/await response)
      (c/string response))))

(defn tell-weather [location reply]
  (if location
    ;; store the location and get weather
    (if-let [raw-weather (api-lookup location)]
      (reply mod-name (niceify raw-weather))
      (reply mod-name (str "This thing is really picky about locations. Try a zip code or"
                           " 3-letter airport code. Also .w is broken so maybe just give up.")))))

(defn location-for-nick [nick channel]
  (:location (first (mongo/get-docs mod-name {:nick nick :channel channel}))))

(defn weather [[speaker channel is-nick content] reply]
  (if (or is-nick (not content))
    ;; .w
    ;; .w @NICK
    (let [nick (s/lower-case (or content speaker))]
      (do ;; get weather for that nick
        (mongo/connect!)
        (if-let [loc (location-for-nick nick channel)]
          (tell-weather loc reply)
          (reply mod-name (str "No location stored for " nick ".")))
        (mongo/disconnect!)))
    ;; .w PLACE
    (let [nick (s/lower-case speaker)]
      (do ;; store location for nick, get weather
        (mongo/connect!)
        (mongo/remove mod-name {:nick nick :channel channel})
        (mongo/insert mod-name {:nick nick :channel channel :location content})
        (mongo/disconnect!)
        (tell-weather content reply)))))

;; syntax: .w [PLACE | @NICK]
(hub/addListener "weather" #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.w(?:eather)?(?: (@)?(.+)?)?$" weather)
;;                            speaker^                 ^channel         is-nick^   ^content
