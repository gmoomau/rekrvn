(ns rekrvn.modules.resistance.engine-test
  (:require [clojure.test :refer :all])
  (:require [rekrvn.modules.resistance.engine :refer :all]))

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
             :players)
             {"cole" {:faction nil :vote nil :is-on-team nil}
              "grog" {:faction nil :vote nil :is-on-team nil}}))
  (let [double-join (-> initial-game-state
                        (join-game "cole")
                        (join-game "cole"))
        messages (:messages double-join)]
    (is (= (:error double-join) :already-joined))
    (is (= (count messages) 1))
    (is (= (first messages)
           [:broadcast "You have already joined this game."]))))

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
    (is (re-find #"(p1|p2|p3|p4|p5)" (:leader game-state)))
    (let [messages (:messages game-state)]
      (is (= (count messages) 11))
      (is (= (first messages)
             [:broadcast "p1 joined the game. [p1]"]))
      (is (= (nth messages 1)
             [:broadcast "p2 joined the game. [p2, p1]"]))
      (is (re-find #"The current mission \(1\) is lead by p[1-5] and requires 2 players and 1 negative vote\(s\) to fail\."
                   (first (rest (nth messages 5))))))))

(defmacro returns-error [error expr]
  `(is (= (:error ~expr) ~error)))

(deftest pick-team-test
  (let [game-state five-player-game-started-state
        leader (:leader game-state)]
    (returns-error :not-leader (pick-team game-state "foo" "foo bar"))
    (returns-error :wrong-team-size (pick-team game-state leader "foo bar baz"))
    (let [picked-state (pick-team game-state leader ["p1" "p2"])]
      (is (= (:phase picked-state) :voting))
      (is (= (get-in picked-state [:players "p1" :is-on-team]) true)))))

(defn choose-team-one [game-state]
  (let [leader (:leader game-state)]
    (pick-team game-state leader ["p1" "p2"])))

(def first-player-voted
  (-> initial-game-state
      (join-game "p1")
      (join-game "p2")
      (join-game "p3")
      (join-game "p4")
      (join-game "p5")
      (start-game)
      (choose-team-one)
      (vote "p1" "pass")))

(def second-player-voted-pass
  (vote first-player-voted "p2" "pass"))

(def second-player-voted-fail
  (vote first-player-voted "p2" "fail"))

(def next-leader
  {"p1" "p2"
   "p2" "p3"
   "p3" "p4"
   "p4" "p5"
   "p5" "p1"})

(defn get-current-mission [game-state]
  (first (:missions game-state)))

(defn choose-team [game-state]
  (let [leader (:leader game-state)
        [team-size _] (first (:missions game-state))
        team (map str (repeat "p") (range 1 (inc team-size)))]
    (pick-team game-state leader team)))

(defn cast-votes [game-state]
  "Whole team votes pass."
  (let [[team-size _] (get-current-mission game-state)
        team (map str (repeat "p") (range 1 (inc team-size)))]
    (reduce (fn [gs player]
              (vote gs player "pass"))
            game-state
            team)))

;(defn cast-votes-naive [game-state]
;  "Whole team votes pass."
;  (let [[team-size _] (get-current-mission game-state)
;        team (map str (repeat "p") (range 1 (inc team-size)))]
;    (reduce (fn [memo player]
;              (assoc-in game-state [:players player :vote] :pass))
;            game-state
;            team)))

(defn play-round [game-state]
  (-> game-state
      (choose-team)
      (cast-votes)))

(defn play-three-round-game [game-state]
  (-> game-state
      (start-game)
      (play-round)
      (play-round)
      (play-round)))

(deftest vote-test
  (let [game-state first-player-voted
        players (:players game-state)
        p1 (players "p1")
        p2 (players "p2")]
    (is (= (:error game-state) nil))
    (is (= (:vote p1) :pass))
    (is (= (:vote p2) nil)))
  (let [leader (:leader first-player-voted)
        game-state second-player-voted-pass
        players (:players game-state)
        score (:score game-state)
        missions (:missions game-state)
        p1 (players "p1")]
    (is (= (count missions) 4))
    (is (= score {:resistance 1
                  :spies 0}))
    (is (= (:is-on-team p1) nil))
    (is (= (:vote p1) nil))
    (is (= (:leader game-state) (next-leader leader)))
    ;(is (= (last (last (:messages game-state)))
    ;       (str "The current mission (2) is lead by "
    ;            (:leader game-state)
    ;            " and requires 3 players and 1 negative vote(s) to fail.")))
    (let [game-state (play-three-round-game five-player-game-state)]
      (is (= (:phase game-state) :inactive))
      (is (= (count (:messages game-state)) 1))
      (is (= (first (:messages game-state))
             [:broadcast "The resistance has won the game!"]))
      (is (= game-state
             (conj initial-game-state
                   {:messages [[:broadcast "The resistance has won the game!"]]}))))))
