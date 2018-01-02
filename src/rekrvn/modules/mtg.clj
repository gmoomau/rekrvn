(ns rekrvn.modules.mtg
  (:require [http.async.client :as c]
            [net.cgrand.enlive-html :as h]
            [rekrvn.hub :as hub]))

(def mod-name "mtg")

(defn web-requst [url]
  (with-open [client (c/create-client)]
    (let [response (c/GET client url)]
      (c/await response)
      (c/string response))))

(defn mtg [[url] reply]
  (let [tree (h/html-resource (java.net.URL. url))
        card-name-html (h/select tree [:.contentTitle :span])
        card-name (-> card-name-html first :content first)]
    (reply mod-name card-name)))

(defn guess-card-url [[card-name] reply]
  (let [base-url "http://gatherer.wizards.com/Pages/Search/Default.aspx?name=+["
        fixed-name (clojure.string/replace card-name " " "]+[")]
  (reply mod-name (str base-url fixed-name "]"))))
        
(hub/addListener mod-name #".*(http://gatherer\.wizards\.com/Pages/Card/Details\.aspx\?\S+)\s*" mtg)
(hub/addListener mod-name #"(?i)^irc.*PRIVMSG \S+ :\.mtg (.+)\s*$" guess-card-url)
