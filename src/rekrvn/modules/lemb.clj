(ns rekrvn.modules.lemb
  (:require [rekrvn.hub :as hub])
  (:require [http.async.client :as c])
  (:use [http.async.client.request :only [url-encode]]))

(def mod-name "lemb")

(defn get-meat [_ reply]
  (with-open [client (c/create-client)]
    (let [url "http://lemb.herokuapp.com/1"
          response (c/GET client url)]
      (c/await response)
      (let [meat (c/string response)]
        (reply mod-name meat)))))

(defn lemb [_ reply]
  (reply mod-name (get-meat)))

(hub/addListener mod-name #"^irc.*PRIVMSG \S+ :\.lemb$" get-meat)
