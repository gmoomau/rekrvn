(defproject rekrvn "0.1.0"
  :description "irc bot framework"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cheshire "5.5.0"]
                 [enlive "1.1.6"]
                 [twitter-api "1.8.0"]
                 [twitter-streaming-client "0.3.2"]
                 [http.async.client "1.2.0"]
                 [com.novemberain/monger "1.4.1"]]
  :main rekrvn.hub
  :test-paths ["test"])
