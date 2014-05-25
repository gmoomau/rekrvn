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

(def sentence-regex
  #"(?x)
      ^(
        (?>
          (?>
            (?>ad|bce?|eg|ex|ie|vs)\.|  # don't stop on ex. vs. ...
            \d++(?>\.\d++)++|           # ex. 1.04 100.3 1.0.3.2
            (?>[^\s^\.]\.)++|           # ex. a.b.c.d., u.s.a.
            [^\s^\.]++)                 # anything without a .
          \s)*+                         # this was all <word><space>*
        [^\s^\.]++                      # the last word in the sentence
        \.)                             # end on a period
      (?>\s|$)                          # the period must either end the
    ")

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
        blurb (second (re-find sentence-regex para))]
    (if (not-empty blurb)
      blurb
      (if (not-empty disambig)
        "could mean a lot of things" ; it's a disambiguation page
        "couldn't figure out the description")))) ; couldn't find a blurb

(defn trigger-from-link [[link] reply]
  (reply mod-name (get-blurb link)))

(defn wiki [[terms] reply]
  (let [link (get-wiki-link terms)
        blurb (get-blurb link)]
    (reply mod-name (str link " - " blurb))))

(hub/addListener mod-name #"^irc.*PRIVMSG \S+ :\.wiki (.+)$" wiki)
(hub/addListener mod-name #"(https?://en\.wikipedia\.org/wiki/\S+)" trigger-from-link)
