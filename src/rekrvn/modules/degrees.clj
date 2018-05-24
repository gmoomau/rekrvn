(ns rekrvn.modules.degrees
  (:require [rekrvn.hub :as hub]))

(def mod-name "degrees")

(defn convert [[n] reply]
  (let [number (float (read-string n))
        fahrenheit (format "%.2f" (+ (* number 1.8) 32))
        celsius (format "%.2f" (/ (- number 32) 1.8))
        radians (format "%.2f" (* 2 (/ number 360)))]
    (reply mod-name (str n "° is " fahrenheit "°F, " celsius "°C, or " radians "π radians."))))

(hub/addListener mod-name #"(\-?\d+(?:\.\d+)?)(?: ?(?:degrees?|°))" convert)
