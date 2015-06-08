(ns rekrvn.modules.weather
  (:require [rekrvn.hub :as hub]
            [rekrvn.modules.mongo :as mongo]
            [http.async.client :as http]
            [clojure.string :as s])
  (:use     [rekrvn.config :only [weather-key]]
            [cheshire.core]
            [http.async.client.request :only [url-encode]]))

(def mod-name "weather")

(defn request [url]
  (with-open [client (http/create-client)]
    (let [response (http/GET client url)]
      (http/await response)
      (parse-string (http/string response) true))))

(defn str-to-loc [location]
  (->> location
    url-encode
    (str "http://autocomplete.wunderground.com/aq?h=0&format=json&query=")
    request
    :RESULTS
    first))

(defn latlon [loc-info]
  (str (:lat loc-info) "," (:lon loc-info)))

(defn get-weather [loc-info]
  (let [query (str
                "https://api.forecast.io/forecast/"
                weather-key "/"
                (latlon loc-info) ","
                (quot (System/currentTimeMillis) 1000))
        weather (request query)]
    (if (:error weather)
      nil
      weather)))

(defn make-forecast [location weather]
  ;; todo: graphs for precip, maybe temp
  ;; grammar for smarter weather
  ;;   ie. build string differently based on values present
  (let [loc (:name location)
        now (:currently weather)
        summary (:summary now)
        temp (:temperature now)
        feels-like (:apparentTemperature now)
        humidity (int (* 100 (:humidity now)))
        wind (:windSpeed now)]
    (str loc ": " summary " | " temp "°F (feels like " feels-like "°F) | " humidity "% humidity | wind " wind "mph")))

(defn store-place [nick channel loc-info]
  (mongo/connect!)
  (mongo/remove mod-name {:nick (s/lower-case nick) :channel channel})
  (mongo/insert mod-name {:nick (s/lower-case nick)
                          :channel channel
                          :loc loc-info})
  (mongo/disconnect!))

(defn get-place [nick channel]
  (mongo/connect!)
  (let [place (first (mongo/get-docs mod-name {:nick (s/lower-case nick) :channel channel}))]
    (mongo/disconnect!)
    place))

(defn forecast-for-nick [[speaker channel nick] reply]
;; .w @nick
  (if-let [place (:loc (get-place nick channel))]
    (if-let [weather (get-weather place)]
      (reply mod-name (make-forecast place weather))
      (reply mod-name (str "Can't get weather for " nick)))
    (reply mod-name (str "Can't find weather for " nick))))

(defn forecast-for-speaker [[nick channel] reply]
;; .w
  (forecast-for-nick [nick channel nick] reply))

(defn forecast-for-location [[nick channel location] reply]
;; .w location
  (if-let [loc-info (str-to-loc location)]
    (if-let [weather (get-weather loc-info)]
      (do
        (store-place nick channel loc-info)
        (reply mod-name (make-forecast loc-info weather)))
      (reply mod-name (str "Can't get weather for " (:name loc-info))))
    (reply mod-name (str "Can't find location: " location))))

;; .w
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.w(?:eather)?\s*$" forecast-for-speaker)
;; .w @nick
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.w(?:eather)?\s+@(\S+)\s*$" forecast-for-nick)
;; .w location
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.w(?:eather)?\s+([^@].+)\s*$" forecast-for-location)

