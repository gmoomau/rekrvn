(ns rekrvn.modules.bitly
  (:require [http.async.client :as c]
            [http.async.client.request :refer [url-encode]]
            [rekrvn.config :refer [bitly-key]]))

(def mod-name "bitly")

(defn- bitly-query [link]
  (str "https://api-ssl.bitly.com/v3/shorten?access_token="
       bitly-key
       "&longUrl="
       (url-encode link)
       "&format=txt"))

(defn shorten-link [long-link]
  (when long-link
    (with-open [client (c/create-client)]
      (let [response (c/GET client (bitly-query long-link))]
        (c/await response)
        (clojure.string/replace (c/string response) "\n" "")))))

