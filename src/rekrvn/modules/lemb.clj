(ns rekrvn.modules.lemb
  (:require [http.async.client :as c]
            [rekrvn.hub :as hub]))

(def mod-name "lemb")

(defn get-meat [_ reply]
  (with-open [client (c/create-client)]
    (let [url "http://lemb.herokuapp.com/1"
          response (c/GET client url)]
      (c/await response)
      (let [meat (clojure.string/replace (c/string response) "\n" " ")]
        (reply mod-name meat)))))

(defn lemb [_ reply]
  (reply mod-name (get-meat)))

(hub/addListener mod-name #"^irc.*PRIVMSG \S+ :\.lemb$" get-meat)
