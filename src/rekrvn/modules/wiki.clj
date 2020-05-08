(ns rekrvn.modules.wiki
  (:require [cheshire.core :refer [parse-string]]
            [http.async.client :as c]
            [http.async.client.request :refer [url-encode]]
            [net.cgrand.enlive-html :as h]
            [rekrvn.config :refer [google-key]]
            [rekrvn.hub :as hub]))

(def mod-name "wiki")
(def query-base (str
                  "https://www.googleapis.com/customsearch/v1"
                  "?key=" google-key
                  "&cx=016151528237284941369:nzaqho1x14w"
                  "&alt=json"
                  "&q="))

(defn- web-request [url]
  (with-open [client (c/create-client)]
    (let [response (c/GET client url)]
      (c/await response)
      (c/string response))))

(defn- is-wiki-link? [result]
  (= "en.wikipedia.org" (:displayLink result)))

(defn- choose-link [results]
  (first (filter is-wiki-link? (:items results))))

(defn- get-wiki-link [terms]
  (let [query (str query-base (url-encode (str "wiki " terms)))
        results (web-request query)
        parsed (parse-string results true)
        wiki (choose-link parsed)]
      (when wiki (clojure.string/replace (:link wiki) #"http:" "https:"))))

(defn- get-title [wiki-link]
  (when-let [title (re-find #"en.wikipedia.org/wiki/(\S+)" wiki-link)]
    (second title)))

(defn- get-blurb [wiki-title]
  (let [wiki-link (str "https://en.wikipedia.org/api/rest_v1/page/summary/" wiki-title)
        results (web-request wiki-link)
        parsed (parse-string results true)]
    (:extract parsed)))

(defn wiki [[search-terms] reply]
  (let [link (get-wiki-link search-terms)
        wiki-title (when link (get-title link))]
    (if wiki-title
      (reply mod-name (str link " - " (get-blurb wiki-title)))
      (reply mod-name "no wiki link found"))))

(defn trigger-from-link [[link mobile title] reply]
  (if mobile
    (let [regular-link (clojure.string/replace link #"en\.m\." "en.")]
      (reply mod-name (str regular-link " - " (get-blurb title))))
    (reply mod-name (get-blurb title))))

(hub/addListener mod-name #"^irc.*PRIVMSG \S+ :\.wiki (.+)$" wiki)
(hub/addListener mod-name #"(https?://en\.(m\.)?wikipedia\.org/wiki/(\S+))" trigger-from-link)
