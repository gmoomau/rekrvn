(defproject rekrvn "0.1.0"
  :description "irc bot framework"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cheshire "5.5.0"]
                 [enlive "1.1.6"]
                 [twitter-api "1.8.0"]
                 [http.async.client "1.2.0"]
                 [com.novemberain/monger "1.4.1"]

                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.2.1"]]
  :main rekrvn.hub
  :test-paths ["test"])
