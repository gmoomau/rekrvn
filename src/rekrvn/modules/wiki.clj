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

(defn first-p [coll]
  (first (filter #(= :p (:tag %)) coll)))

(defn strip-html [raw]
  ;; remove anything inside html tags
  (let [matcher #"^(.*)<[^>]+>(.*)$"]
    (if-let [[_ & surround] (re-find matcher raw)]
      (recur (apply str surround))
      raw)))

(defn get-blurb [url]
  (let [full-page (web-request (str url "?action=render"))
        tree (h/as-hickory (h/parse full-page))
        intro (-> tree :content first :content second :content first-p h/hickory-to-html)
        stripped (strip-html intro)]
    ;; only return the first sentence
    (second (re-find #"^(.+?\.) [A-Z]" stripped))))

(defn wiki [[terms] reply]
  (let [link (get-wiki-link terms)]
    (reply mod-name (str link " - " (get-blurb link)))))

(hub/addListener mod-name #"^irc.*PRIVMSG \S+ :\.wiki (.+)$" wiki)
