(ns rekrvn.modules.weather
  (:require [cheshire.core :refer [parse-string]]
            [http.async.client :as http]
            [http.async.client.request :refer [url-encode]]
            [rekrvn.config :refer [weather-key]]
            [rekrvn.hub :as hub]
            [rekrvn.modules.bitly :as bitly]
            [rekrvn.modules.mongo :as mongo]))

(def mod-name "weather")

(defn request [url]
  (try
    (with-open [client (http/create-client)]
      (let [response (http/GET client url)]
        (http/await response)
        (parse-string (http/string response) true)))
    (catch Exception e (println (str "Caught exception: " (.getMessage e))) nil)))

(defn str-to-loc [location]
  (->> location
    url-encode
    (str "http://autocomplete.wunderground.com/aq?h=0&format=json&exclude=minutely&query=")
    request
    :RESULTS
    first))

(defn latlon [loc-info]
  (str (:lat loc-info) "," (:lon loc-info)))

(defn get-weather [loc-info]
  (let [query (str
                "https://api.forecast.io/forecast/"
                weather-key "/"
                (latlon loc-info))
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


(defn make-forecast [location weather]
  (let [lbracket (str (char 3) "14[" (char 3))
        rbracket (str (char 3) "14]" (char 3))
        loc (:name location)
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
        rain-spark (make-sparkline (map :precipProbability hourly) 0 1)
        alert-title (-> weather :alerts first :title)
        alert-link (-> weather :alerts first :uri bitly/shorten-link)]
    (str loc
         (when alert-title
           (str " " lbracket "05" alert-title (char 3) " " alert-link " " rbracket))
         " " lbracket  now-str  rbracket " "
         lbracket "Upcoming: " hourly-summary " | " lo "°  " hi "°"; | " temp-spark
         (when (> max-rain 0)
           (str " | " max-rain "% chance of water falling from the sky "
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
      (reply mod-name (str "Can't get weather for " (:name loc-info))))
    (reply mod-name (str "Can't find location: " location))))

;; TODO: refactor because a lot of work is duplicated

;; .w
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.w(?:eather)?\s*$" forecast-for-speaker)
;; .w @something
(hub/addListener mod-name #"^.*PRIVMSG (\S+) :\.w(?:eather)?\s+@(.+?)\s*$" check-forecast)
;; .w location
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.w(?:eather)?\s+([^@].+)\s*$" forecast-for-location)

