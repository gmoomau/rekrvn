(ns rekrvn.modules.factcheck
  (:require [rekrvn.hub :as hub]))

(def mod-name "factcheck")

(def sources [["wikipedia" (range 2001 2018)]
              ["the onion" (range 1998 2018)]
              ["google" (range 1998 2018)]
              ["nostradamus" (range 1503 1566)]
              ["new york times" (range 1851 2018)]
              ["washington post" (range 1877 2018)]
              ["lmgtfy" (range 2008 2018)]
              ["the bible" (range 50 100)]
              ["dead sea scrolls" (range -408 318)]

              ])

(defn fact-check [_ reply]
  (let [veracity (rand-nth ["TRUE" "MOSTLY TRUE" "SOMEWHAT TRUE" "MOSTLY FALSE" "FALSE"])
        [source years] (rand-nth sources)
        year (rand-nth years)]
    (reply mod-name (str veracity " (" source ", " year ")"))))

(hub/addListener mod-name #"(?i)^irc.*PRIVMSG \S+ :\.factcheck(?:\s.*)?$" fact-check)

;; more sources:
;; 13:20 < mvid> the darkness
;; 13:20 < pmcq> gandalf
;; 13:20 < mvid> library of alexandria
;; 13:21 < mvid> wikileaks
;; 13:21 < pmcq> uncle vito
;; 13:21 < mvid> "it is known"
;; 13:22 < grog> my uncle who works at microsoft
;; 13:23 < grog> i'll add a bunch
;; 13:23 < mvid> also the dates should match the sources
;; 13:24 < grog> effort
;; 13:24 < pmcq> zen master from the himalayas
;; 13:24 < grog> actually that's not too bad
;; 13:24 < pmcq> i forget what the actual word is that i want to use for that
;; 13:24 < grog> i'll just use a map instead of 2 vecs
;; 13:24 < grog> one random call, destructure
;; 13:24 < mvid> mhm
;; 13:24 < grog> wait it's still 2 but it's easy enough
;; 13:24 < pmcq> inner wisdom
;; 13:25 < pmcq> old wives tale
;; 13:26 < mvid> common sense
;; 13:26 < mvid> books
;;
