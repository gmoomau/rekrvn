(ns rekrvn.modules.weather
  (:require [cheshire.core :refer [parse-string]]
            [http.async.client :as http]
            [http.async.client.request :refer [url-encode]]
            [rekrvn.config :refer [weather-key version]]
            [rekrvn.hub :as hub]
            [rekrvn.modules.db :as db]
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
  (try
    (let [query (str
                  "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/"
                  loc-info
                  "?unitGroup=us&key="
                  weather-key
                  "&contentType=json")
          weather (request query)]
      (if (:error weather)
        nil
        weather))
    (catch Exception e (log/error "error getting weather for" loc-info " " e) nil))
  )

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
        loc (:resolvedAddress weather)
        today (-> weather :days first)
        humidity (-> today :humidity int)
        wind (-> today :windgust int)
        today-str (str "Now: " (:description today) " | " (:temp today) "°F"
                     (when (or (< humidity 30) (> humidity 65))
                       (str " | " humidity "% humidity"))
                     (when (> wind 30)
                       (str " | wind " wind "mph")))
        ;hourly (take 24 (-> weather :hourly :data))
        ;hourly-summary (-> weather :hourly :summary)
        hi (str (char 3) "07" (char 0x200B) (-> today :tempmax int inc) (char 3))
        lo (str (char 3) "11" (char 0x200B) (-> today :tempmin int) (char 3))
        ;temp-spark (make-sparkline (map :temperature hourly))
        ;rain-chance (int (* 100 (apply max (map :precipProbability hourly))))
        rain-chance (-> today :precipprob int)
        rain-type (-> today :preciptype first)
        ;rain-spark (make-sparkline (map :precipProbability hourly) 0 1)
        alert (-> weather :alerts first :event)
        moon-phase (:moonphase today)]
    (str loc
         (when alert
           (str " " lbracket "05" alert (char 3) rbracket))
         (when (< 0.45 moon-phase 0.55)
           (str " " lbracket (char 3) "08Warning: werewolves" (char 3) rbracket))
         " " lbracket  today-str  rbracket " "
         ;lbracket "Upcoming: " hourly-summary " | " lo "°  " hi "°"; | " temp-spark
         lbracket "Today | " lo "°  " hi "°"
         (when (and rain-type (> rain-chance 0))
           (str " | " rain-chance "% chance of " rain-type
               ; (when (>= rain-chance 20) (str " " (char 3) "02" rain-spark))
                ))
         rbracket)))

(defn store-home [nick channel location]
  (db/remove! mod-name {:nick (clojure.string/lower-case nick) :channel channel})
  (db/insert! mod-name {:nick (clojure.string/lower-case nick)
                        :channel channel
                        :location location}))

(defn get-home [nick channel]
  ;; checks db for home stored for nick/channel
  (first (db/get-all-docs mod-name {:nick (clojure.string/lower-case nick) :channel channel})))


(defn check-forecast [[channel query] reply]
  ;; .w @some string
  ;; does not save anything to the db
  ;; first check if the string is someone's nick. if it is find weather for them
  ;; if not, treat it like a location and find weather
  (let [place (or (:location (get-home query channel)) (url-encode query))]
    (if-let [weather (get-weather place)]
      (reply mod-name (make-forecast place weather))
      (reply mod-name (str "Can't find weather for " query)))))

(defn forecast-for-speaker [[nick channel] reply]
  ;; .w
  (check-forecast [channel nick] reply))

(defn forecast-for-location [[nick channel location] reply]
  ;; .w location
  (let [loc-info (url-encode location)]
    (if-let [weather (get-weather loc-info)]
      (do
        (store-home nick channel loc-info)
        (reply mod-name (make-forecast loc-info weather)))
      (reply mod-name (str "Can't get weather for " loc-info)))))

;; TODO: refactor because a lot of work is duplicated <--- is this true still?
;; TODO: separate out the @ into @ and !

;; .w
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.w(?:eather)?\s*$" forecast-for-speaker)
;; .w @something
(hub/addListener mod-name #"^.*PRIVMSG (\S+) :\.w(?:eather)?\s+@(.+?)\s*$" check-forecast)
;; .w location
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG (\S+) :\.w(?:eather)?\s+([^@].+)\s*$" forecast-for-location)
