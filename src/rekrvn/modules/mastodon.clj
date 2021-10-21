(ns rekrvn.modules.mastodon
  (:require [http.async.client :as c] ; don't think i need http.async.client
            [net.cgrand.enlive-html :as h]
            [rekrvn.hub :as hub]
            [clojure.tools.logging :as log]))

(def mod-name "mastodon")

(defn mastodon [[url author] reply]
  (try
    (let [tree (h/html-resource (java.net.URL. url))
          text (-> (h/select tree [:head (h/attr= :property "og:description")]) first :attrs :content)
          ;embed (-> (h/select tree [:head (h/attr= :property "og:image")]) first :attrs :content)
          ; TODO: extract/display link to attached media. the line doesn't
          ;       work because there can be multiple embeds or none
          ;       if there are none then the avatar will be embedded
          ;       also og:description is ugly if an image is included
          ]
      (when text
        (reply mod-name (str (char 3) "10" author (char 15) " " text))))
    (catch Exception e (log/error "mastodon exception: " e))))

(defn pleroma [[url] reply]
  (try
    (let [tree (h/html-resource (java.net.URL. url))
          text (-> (h/select tree [:head (h/attr= :property "og:description")]) first :attrs :content)]
      (when text (reply mod-name text)))
    (catch Exception e (log/error "pleroma exception: " e))))

(defn pleroma-but-uglier [[url] reply]
  (try
    (let [tree (h/html-resource (java.net.URL. url))
          og-url (-> (h/select tree [:head (h/attr= :property "og:url")]) first :attrs :content)]
      (when og-url (pleroma [og-url] reply)))
    (catch Exception e (log/error "pleroma exception: " e))))

(hub/addListener mod-name #".*(https?://\S+/(@\S+)/\d+)\s*" mastodon)
(hub/addListener mod-name #".*(https?://\S+/notice/[A-Za-z0-9]+)\s*" pleroma)
(hub/addListener mod-name #".*(https?://\S+/objects/\S+)\s*" pleroma-but-uglier)
; TODO pleroma improvements
; 1) parse out and colorize author
; 2) it doesn't show the "attached: 1 image" thing, has the same issue where it uses the icon if none is present
