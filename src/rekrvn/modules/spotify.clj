(ns rekrvn.modules.spotify
  (:require [rekrvn.hub :as hub])
  (:require [http.async.client :as c])
  (:use [cheshire.core])
  )

(def modName "spotify")

(defn niceify [track]
  (str (:name (first (:artists (:track track)))) " - " (:name (:track track))))

(defn apiLookup [id]
  (with-open [client (c/create-client)]
    (let [url (str "http://ws.spotify.com/lookup/1/.json?uri=spotify:track:" id)
          response (c/GET client url)]
      ;; wait for response to be received
      (c/await response)
      ;; read body of response as string
      (c/string response)
  )))

(defn trackStr [[linktype id] reply]
  (when-let [jsn (apiLookup id)]
    (let [parsed (parse-string jsn true)
          msg (niceify parsed)]
      (when reply
        (if (re-matches #"spotify:track:" linktype)
          (reply modName (str msg "   http://open.spotify.com/track/" id))
          (reply modName msg)))
      )))

(hub/addListener
  modName #"(http://open.spotify.com/track/|spotify:track:)([\d\w]+)" trackStr)
