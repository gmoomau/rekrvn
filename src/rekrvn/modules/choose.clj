(ns rekrvn.modules.choose
  (:require [rekrvn.hub :as hub]
            [clojure.string :refer [split trim]]
            [clojure.tools.logging :as log]))

(def mod-name "choose")

(defn choose [[things] reply]
  (let [choices (split things #"\|+|,+")]
    (if (> (count choices) 1)
      (reply mod-name (trim (rand-nth choices)))
      (reply mod-name (trim (rand-nth (split things #" +")))))))

(hub/addListener mod-name #"^irc.*PRIVMSG \S+ :\.choose \s*(.+)$" choose)
