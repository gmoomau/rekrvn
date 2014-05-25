(defproject rekrvn "0.0.8-alpha"
  :description "irc bot framework"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [http.async.client "0.4.3"]
                 [cheshire "4.0.0"]
                 [enlive "1.1.1"]
                 [twitter-api "0.7.5"]
                 [com.novemberain/monger "1.4.1"]]
  :main rekrvn.hub
  :test-paths ["test"])
