(ns rekrvn.modules.factcheck
  (:require [rekrvn.hub :as hub]))

(def mod-name "factcheck")

(def sources [["wikipedia" (range 2001 2021)]
              ["the onion" (range 1998 2021)]
              ["google" (range 1998 2021)]
              ["nostradamus" (range 1503 1566)]
              ["new york times" (range 1851 2021)]
              ["washington post" (range 1877 2021)]
               ["lmgtfy" (range 2008 2021)]
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
;; d> the darkness
;; q> gandalf
;; d> library of alexandria
;; d> wikileaks
;; q> uncle vito
;; d> "it is known"
;; g> my uncle who works at microsoft
;; g> i'll add a bunch
;; d> also the dates should match the sources
;; g> effort
;; q> zen master from the himalayas
;; q> inner wisdom
;; q> old wives tale
;; d> common sense
;; d> books
;; white house press briefing
;; g> he could just cite himself ;; g> (name, %timestamp)
