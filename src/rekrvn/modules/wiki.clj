(ns rekrvn.modules.wiki
  (:require [rekrvn.hub :as hub])
  (:use [rekrvn.config :only [google-key]])
  (:require [http.async.client :as c])
  (:use [http.async.client.request :only [url-encode]])
  (:use [cheshire.core]))

(def mod-name "wiki")
(def query-base (str
                 "https://www.googleapis.com/customsearch/v1"
                 "?key=" google-key
                 "&cx=016151528237284941369:nzaqho1x14w"
                 "&alt=json"
                 "&q="))

(defn is-wiki-link? [result]
  (= "en.wikipedia.org" (:displayLink result)))

(defn choose-link [results]
  (first (filter is-wiki-link? (:items results))))

(defn get-wiki-link [terms]
  (with-open [client (c/create-client)]
    (let [query (str query-base (url-encode (str "wiki " terms)))
          response (c/GET client query)
          results (-> response c/await c/string (parse-string true))
          wiki (choose-link results)]
      (:link wiki))))

(defn wiki [[terms] reply]
  (reply mod-name (get-wiki-link terms)))

(hub/addListener mod-name #"^irc.*PRIVMSG \S+ :\.wiki (.+)$" wiki)

