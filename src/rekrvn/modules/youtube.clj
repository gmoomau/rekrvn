(ns rekrvn.modules.youtube
  (:require [rekrvn.hub :as hub])
  (:require [clojure.xml :as xml]))

(def mod-name "youtube")

(defn niceify [title]
  (when title
    (str (char 3) "1,0You" (char 3) "0,5Tube" (char 3) " " title)))

(defn youtube [[vid] reply]
  (try ; xml/parse throws IOException on a bad url
    (let [url (str "http://gdata.youtube.com/feeds/api/videos/" vid "?fields=title")
          xml (xml/parse url)
          title (-> xml :content first :content first)
          msg (niceify title)]
      (reply mod-name msg))
    (catch Exception e (str "Caught exception: " (.getMessage e)))))

(hub/addListener "youtube" #"(?:youtu\.be/|youtube\.com/(?:watch\?\S*v=|embed/))([A-Za-z0-9_-]+)" youtube)
