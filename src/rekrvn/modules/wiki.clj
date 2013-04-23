(ns rekrvn.modules.wiki
  (:require [rekrvn.hub :as hub])
  (:use [rekrvn.config :only [google-key]])
  (:require [http.async.client :as c])
  (:use [http.async.client.request :only [url-encode]])
  (:require [net.cgrand.enlive-html :as h])
  (:require [clojure.string :as s])
  (:use [cheshire.core]))

(def mod-name "wiki")
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

(defn is-wiki-link? [result]
  (= "en.wikipedia.org" (:displayLink result)))

(defn choose-link [results]
  (first (filter is-wiki-link? (:items results))))

(defn get-wiki-link [terms]
  (let [query (str query-base (url-encode (str "wiki " terms)))
        results (web-request query)
        parsed (parse-string results true)
        wiki (choose-link parsed)]
    (:link wiki)))

(defn strip-formatting [raw]
  (-> raw
    (s/replace #"<[^>]+>" "")
    (s/replace #"\[\d+\]" "")))

(defn get-blurb [url]
  (let [tree (h/html-resource (java.net.URL. url))
        paras (h/select tree [:#mw-content-text :p])
        disambig (h/select tree [:#disambigbox])
        html-ps (map (comp strip-formatting (partial apply str) h/emit*) paras)
        para (first (filter not-empty html-ps))
        sentence (second (re-find #"^(.+?\.)(?: [A-Z])?" para))]
    (if (and (not-empty disambig) (empty? sentence))
      "could mean a lot of things" ; it's a disambiguation page
      (second (re-find #"^(.+?[a-zA-Z][a-zA-Z]\.)(?: [A-Z])?" para))))) ; first sentence of summary

(defn trigger-from-link [[link] reply]
  (reply mod-name (get-blurb link)))

(defn wiki [[terms] reply]
  (let [link (get-wiki-link terms)
        blurb (get-blurb link)]
    (reply mod-name (str link (when blurb (str " - " blurb))))))

(hub/addListener mod-name #"^irc.*PRIVMSG \S+ :\.wiki (.+)$" wiki)
(hub/addListener mod-name #"(https?://en\.wikipedia\.org/wiki/\S+)" trigger-from-link)
