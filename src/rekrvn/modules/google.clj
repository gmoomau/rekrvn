(ns rekrvn.modules.google
  (:require [rekrvn.hub :as hub])
  (:use [rekrvn.config :only [google-key]])
  (:use [rekrvn.modules.twitter :only [plaintext]])
  (:require [http.async.client :as c])
  (:use [http.async.client.request :only [url-encode]])
  (:require [net.cgrand.enlive-html :as h])
  (:require [clojure.string :as s])
  (:use [cheshire.core]))

(def mod-name "google")
(def query-base (str
                  "https://www.googleapis.com/customsearch/v1"
                  "?key=" google-key
                  "&cx=016151528237284941369:nzaqho1x14w"
                  "&alt=json"
                  "&q="))

(defn web-request [url]
  (with-open [client (c/create-client)]
    (let [response (c/GET client url)]
      (c/await response)
      (c/string response))))

(defn get-top [search-str]
  (let [query (str query-base (url-encode search-str))
        results (web-request query)
        parsed (parse-string results true)
        top (first (:items parsed))]
    (str (:snippet top) " - " (:link top))))

(defn search-url [search-str]
  (str "http://www.google.com/search?q=" (url-encode search-str)))

(defn google [[search-str] reply]
  (let [all-results (search-url search-str)
        top-result (str "\"" (plaintext (get-top search-str)) "\"")]
    (if top-result
      (reply mod-name (str top-result " more: " all-results))
      (reply mod-name all-results))))

(hub/addListener mod-name #"^irc.*PRIVMSG \S+ :\.g(?:oogle)? (.+)$" google)
