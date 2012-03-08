(ns rekrvn.core
  (:use rekrvn.irc))

(defn -main [& args]
  (let [flounder {:server "flounder.dyndns.org" :port 6998 :nick "test" :name "botty botson"}
        irc (connect flounder)]))
