(ns rekrvn.modules.stocks
  (:require [cheshire.core :refer [parse-string]]
            [http.async.client :as c]
            [rekrvn.hub :as hub]
            [rekrvn.config :refer [iex-key]]
            [clojure.tools.logging :as log]))

(def mod-name "stocks")

(def sparks ["▁" "▂" "▃" "▄" "▅" "▆" "▇" "█"])
(defn make-sparkline
  ([data] (make-sparkline data (apply min data) (apply max data)))
  ([data low high]
   (let [step (* 1.00001 (/ (- high low) (count sparks)))
         ; *1.00001 so that max doesn't cause array index out of bounds
         heights (map #(int (/ (- % low) step)) data)]
     (apply str (map sparks heights)))))

(defn web-request [url]
  (with-open [client (c/create-client)]
    (let [response (c/GET client url)]
      (c/await response)
      (c/string response))))

(def base-url "https://cloud.iexapis.com/stable/stock/")
(defn get-price [[stock-symbol] reply]
  (let [earlier (web-request (str base-url stock-symbol "/chart/5d?token=" iex-key))
        today (web-request (str base-url stock-symbol "/quote?token=" iex-key))]
    (if-not (= today "Unknown symbol")
      (let [earlier-results (parse-string earlier true)
            today-results (parse-string today true)
            current-price (float (:latestPrice today-results))
            day-change (when-let [_ (:changePercent today-results)] (* 100 (float _)))
            prices (conj (vec (map #(float (:close %)) earlier-results)) current-price)
            graph (make-sparkline prices)
            company (:companyName today-results)
            lo-str (str (char 3) "05" (char 0x200B) (format "%.2f" (apply min prices)) (char 3))
            hi-str (str (char 3) "03" (char 0x200B) (format "%.2f" (apply max prices)) (char 3))
            change-str (when day-change
                         (if (> day-change 0)
                           (str (char 3) "03▴" (format "%.2f" day-change))
                           (str (char 3) "05▾" (format "%.2f" (Math/abs day-change)))))]
        (reply mod-name (str company " - " lo-str " " hi-str " " graph " "
                             (format "%.2f" current-price)
                             (when change-str (str " (" change-str "%" (char 3) ")")))))
      (reply mod-name (str "Can't find $" stock-symbol)))))

(hub/addListener mod-name #"^irc.*PRIVMSG \S+ :\.sto (\S+)\s*$" get-price)
(hub/addListener mod-name #"\$([a-z]{1,5})(?:\s|$)" get-price)
