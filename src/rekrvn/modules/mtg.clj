(ns rekrvn.modules.mtg
  (:require [rekrvn.hub :as hub])
  (:require [http.async.client :as c])
  (:require [net.cgrand.enlive-html :as h])
  (:use [cheshire.core]))

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
        
(hub/addListener mod-name #"(http://gatherer\.wizards\.com/Pages/Card/Details\.aspx\?multiverseid=\d+)" mtg)
