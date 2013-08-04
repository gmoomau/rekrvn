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
  (is (= (:phase five-player-game-started-state)
         :pick-team)))
