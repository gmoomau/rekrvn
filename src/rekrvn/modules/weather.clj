(ns rekrvn.modules.weather
  (:require [cheshire.core :refer [parse-string]]
            [http.async.client :as http]
            [http.async.client.request :refer [url-encode]]
            [rekrvn.config :refer [weather-key version]]
            [rekrvn.hub :as hub]
            [rekrvn.modules.mongo :as mongo]
            [clojure.tools.logging :as log]))

(def mod-name "weather")

(defn request [url]
  (try
    (with-open [client (http/create-client)]
      (let [user-agent {:User-Agent (str "rekrvn/" version)} ; UA required by nominatim api
            response (http/GET client url :headers user-agent)]
        (http/await response)
        (parse-string (http/string response) true)))
    (catch Exception e (println (str "Caught exception: " (.getMessage e))) nil)))

(defn str-to-loc [location]
  (->> location
    url-encode
    (str "https://nominatim.openstreetmap.org/search.php?limit=1&accept-language=en-US&format=jsonv2&q=")
    request
    first))

(defn latlon [loc-info]
  (str (:lat loc-info) "," (:lon loc-info)))

(defn get-weather [loc-info]
  (let [query (str
                "https://api.forecast.io/forecast/"
                weather-key "/"
                (latlon loc-info)) ; for copy/pasting: 39.0000,-77.0999
                ;"," (quot (System/currentTimeMillis) 1000))
        weather (request query)]
    (if (:error weather)
      nil
      weather)))

(def sparks ["_" "▁" "▂" "▃" "▄" "▅" "▆" "▇" "█"])
(defn make-sparkline
  ([data] (make-sparkline data (apply min data) (apply max data)))
  ([data low high]
    ; _ for low, otherwise normal sparks. this is why there's (dec ..)
    ;    and (- .. 0.001) and (inc (Math/floor ..)) in the map
    (let [step (* 1.001 (/ (- high low) (dec (count sparks))))
      ; *1.001 so that max doesn't cause array index out of bounds
      heights (map #(inc (int (Math/floor (/ (- % low 0.001) step)))) data)]
      (apply str (map sparks heights)))))

(defn precip-type [types]
  (when (> (count types) 0)
    (->> types
      frequencies
      (apply max-key val); this threw for some reason
      ; clojure.lang.ArityException: Wrong number of args (1) passed to: core/max-key
      first)))

(defn make-forecast [location weather]
  (let [lbracket (str (char 3) "14[" (char 3))
        rbracket (str (char 3) "14]" (char 3))
        loc (:display_name location)
        now (:currently weather)
        humidity (int (* 100 (:humidity now)))
        wind (int (:windSpeed now))
        now-str (str "Now: " (:summary now) " | " (:temperature now) "°F"
                     (when (or (< humidity 30) (> humidity 65))
                       (str " | " humidity "% humidity"))
                     (when (> wind 30)
                       (str " | wind " wind "mph")))
        hourly (take 24 (-> weather :hourly :data))
        hourly-summary (-> weather :hourly :summary)
        hi (str (char 3) "07" (char 0x200B) (inc (int (apply max (map :temperature hourly)))) (char 3))
        lo (str (char 3) "11" (char 0x200B) (int (apply min (map :temperature hourly))) (char 3))
        ;temp-spark (make-sparkline (map :temperature hourly))
        max-rain (int (* 100 (apply max (map :precipProbability hourly))))
        rain-type (precip-type (remove nil? (map :precipType hourly)))
        rain-spark (make-sparkline (map :precipProbability hourly) 0 1)
        alert-title (-> weather :alerts first :title)
        moon-phase (-> weather :daily :data first :moonPhase)]
    (str loc
         (when alert-title
           (str " " lbracket "05" alert-title (char 3) rbracket))
         (when (< 0.45 moon-phase 0.55)
           (str " " lbracket (char 3) "08Warning: werewolves" (char 3) rbracket))
         " " lbracket  now-str  rbracket " "
         lbracket "Upcoming: " hourly-summary " | " lo "°  " hi "°"; | " temp-spark
         (when (> max-rain 0)
           (str " | " max-rain "% chance of " rain-type
                (when (>= max-rain 20) (str " " (char 3) "02" rain-spark))))
         rbracket)))

(defn store-home [nick channel loc-info]
  ;; saves home for nick/channel in db
  (mongo/connect!)
  (mongo/remove mod-name {:nick (clojure.string/lower-case nick) :channel channel})
  (mongo/insert mod-name {:nick (clojure.string/lower-case nick)
                          :channel channel
                          :loc loc-info})
  (mongo/disconnect!))

(defn get-home [nick channel]
  ;; checks db for hom stored for nick/channel
  (mongo/connect!)
  (let [place (first (mongo/get-docs mod-name
                                     {:nick (clojure.string/lower-case nick)
                                      :channel channel}))]
    (mongo/disconnect!)
    place))

(defn check-forecast [[channel query] reply]
  ;; .w @some string
  ;; does not save anything to the db
  ;; first check if the string is someone's nick. if it is find weather for them
  ;; if not, treat it like a location and find weather
  (let [place (or (:loc (get-home query channel)) (str-to-loc query))]
    (if place
      (when-let [weather (get-weather place)]
        (reply mod-name (make-forecast place weather)))
      (reply mod-name (str "Can't find weather for " query)))))

(defn forecast-for-speaker [[nick channel] reply]
  ;; .w
  (check-forecast [channel nick] reply))

(defn forecast-for-location [[nick channel location] reply]
  ;; .w location
  (if-let [loc-info (str-to-loc location)]
    (if-let [weather (get-weather loc-info)]
      (do
        (store-home nick channel loc-info)
        (reply mod-name (make-forecast loc-info weather)))
      (reply mod-name (str "Can't get weather for " (:display_name loc-info))))
    (reply mod-name (str "Can't find location: " location))))

;; TODO: refactor because a lot of work is duplicated

;; .w
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.w(?:eather)?\s*$" forecast-for-speaker)
;; .w @something
(hub/addListener mod-name #"^.*PRIVMSG (\S+) :\.w(?:eather)?\s+@(.+?)\s*$" check-forecast)
;; .w location
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.w(?:eather)?\s+([^@].+)\s*$" forecast-for-location)
