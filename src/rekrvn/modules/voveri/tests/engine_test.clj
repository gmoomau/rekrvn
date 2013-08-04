(ns rekrvn.modules.voveri.tests.engine-test
  (:require [clojure.test :refer :all])
  (:require [rekrvn.modules.voveri.engine :refer :all]))

(defn- noop [])

(deftest in-phase-test
  (is (= (:error (in-phase initial-game-state :foo (noop)))
         :wrong-phase))
  (is (= (in-phase initial-game-state :inactive :success)
         :success)))

(deftest join-game-test
  (is (= (-> initial-game-state
             (join-game "cole")
             :players)
         {"cole" {:faction nil :vote nil :is-on-team nil}}))
  (is (= (-> initial-game-state
             (join-game "cole")
             :phase)
         :inactive))
  (is (= (-> initial-game-state
             (join-game "cole")
             (join-game "grog")
             {"cole" {:faction nil :vote nil :is-on-team nil}
              "grog" {:faction nil :vote nil :is-on-team nil}}))))

(def five-player-game-state
  (-> initial-game-state
      (join-game "p1")
      (join-game "p2")
      (join-game "p3")
      (join-game "p4")
      (join-game "p5")))

(def five-player-game-started-state
  (start-game five-player-game-state))

(deftest start-game-test
  (let [game-state five-player-game-started-state]
    (is (= (:phase game-state)
           :pick-team))
    (is (= (:leader game-state)))
    (is (= (:score game-state)
           {:resistance 0 :spies 0}))
    (let [missions (:missions game-state)]
      (is (= (count missions) 5))
      (is (= (first missions) [2 1])))
    (is (and (< (:leader game-state 5))
             (>= (:leader game-state 0))))
    (let [messages (:messages game-state)]
      (is (= (count messages) 6))
      (is (= (first messages)
             [:broadcast "p1 joined the game. [p1]"]))
      (is (= (nth messages 1)
             [:broadcast "p2 joined the game. [p2, p1]"]))
      (is (re-find #"The current mission \(1\) is led by p[1-5] and requires 2 players and 1 negative votes to fail\."
                   (first (rest (nth messages 5))))))))
