(ns rekrvn.modules.mastodon
  (:require [http.async.client :as c]
            [net.cgrand.enlive-html :as h]
            [rekrvn.hub :as hub]))

(def mod-name "mastodon")

(defn mastodon [[url author] reply]
  (let [tree (h/html-resource (java.net.URL. url))
        text (-> (h/select tree [:head (h/attr= :property "og:description")]) first :attrs :content)
        ;embed (-> (h/select tree [:head (h/attr= :property "og:image")]) first :attrs :content)
        ; TODO: extract/display link to attached media. the line doesn't
        ;       work because there can be multiple embeds or none
        ;       if there are none then the avatar will be embedded
        ;       also og:description is ugly if an image is included
        ]
    (when text
      (reply mod-name (str (char 3) "10" author (char 15) " " text)))))

(hub/addListener mod-name #".*(https?://\S+/(@\S+)/\d+)\s*" mastodon)
