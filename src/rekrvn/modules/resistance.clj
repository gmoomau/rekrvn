(ns rekrvn.modules.resistance
  (:require [rekrvn.hub :as hub])
  (:require [clojure.string :as s]))

;; Initial game state

(def initial-game-state
  "The defaults for the game state."
  {:state :inactive
   :players []
   :missions []
   :leader nil
   :current-team []
   :score {:resistance 0
           :spies 0}})

;; Team balancing. Resistance / Spies.

(defmacro gen-teams [r s]
  "Generate a team with <r> resistance members and
   <s> spies."
  `(concat
    (repeat ~r :resistance)
    (repeat ~s :spies)))

(def team-balances
  "The balances of resistance vs spies for valid game sizes."
  {5  (gen-teams 3 2)
   6  (gen-teams 4 2)
   7  (gen-teams 4 3)
   8  (gen-teams 5 3)
   9  (gen-teams 6 3)
   10 (gen-teams 6 4)})

(def mission-team-sizes
  "The mission info. Each entry in the vector corresponds
   to the number of players required in each mission. If
   the entry is a number, the mission requires one (default)
   negative to fail. If the entry is a vector, the first
   item is the number of players and the second item is the
   number of negatives required for failure."
  {5  [2 3 2 3 3]
   6  [2 3 4 3 4]
   7  [2 3 3 [4 2] 4]
   8  [3 4 4 [5 2] 5]
   9  [3 4 4 [5 2] 5]
   10 [3 4 4 [5 2] 5]})

(defn expand-mission-team-info [info]
  "Expands based on the protocol for defining team sizes."
  (cond
   (number? info) [info 1]
   (vector? info) info))

(defn get-mission-team-sizes [size]
  (let [mission-team-size (get mission-team-sizes size)]
    (map expand-mission-team-info mission-team-size)))

(defn set-teams [players]
  "Given a list of players, return the players with proper
   assignments, returning nil when the number of players
   is too large or too small."
  (when-let [initial-teams (get team-balances (count players))]
    (let [assignments (shuffle initial-teams)]
      (map #(conj %1 {:team %2}) players assignments))))

;; Global game state
(def game-state
  (ref initial-game-state))

;; Utility Fns

(defmacro in-state-sync [state & forms]
  "When the game is in the given state, evaluate the forms.
   Otherwise, return nil."
  `(dosync
    (when (= (:state @game-state) ~state)
      (do ~@forms))))

;; User actions

(def join-game [name]
  "Adds a user to the game if the game has not started.
   Returns true on success and nil on failure."
  (in-state-sync
   :inactive
   (let [players (:players @game-state)
         new-players (cons {:name name
                            :team nil
                            :current-vote nil}
                           players)]
     (alter game-state conj {:players new-players})
     :player-joined)))

(defn start-game []
  "Initializes a game. Sets state to active, chooses teams,
   notifies the players of their teams and starts the game."
  (in-state-sync
   :inactive
   (let [players (:players @game-state)
         num-players (count players)
         new-state {:players (set-teams players)
                    :missions (get-mission-team-sizes num-players)
                    :state :pick-team
                    :leader (rand-int num-players)}]
     (alter game-state conj new-state)
     :game-started)))

(defn is-player? [name]
  "Verify the existence of a player with the name <name>"
  (let [players (:players @game-state)]
    (any? #(= (:name %) name) players)))

(defn is-on-team? [name]
  "Verify <name> is on the current name."
  (let [team (:current-team @game-state)]
    (some #(= name (:name %)) team)))

(defn get-player [name]
  "Retrieve the given player from the list."
  (let [players (:players @game-state)]
    (some #(= (:name %) name) players)))

(defn pick-team [team]
  "Allow the current leader to choose their team."
  (in-state-sync
   :pick-team
   (when (every? is-player? team)
     (alter game-state
            conj
            {:current-team team
             :state :voting})
     :team-ready)))

(defn update-player [name vals]
  "Update the player with the values in vals."
  (let [players (:players @game-state)]
    (map (fn [p]
           (if (= name (:name p))
             (conj player vals)
             p)))))

(defmacro valid-vote? [vote]
  "Verify the vote is for or against."
  `(some #(= % ~vote) ["for" "against"]))

(defn votes-cast []
  "Return the number of votes already cast."
  (let [players (:players @game-state)
        reduce-fn (fn [l r]
                    (+ l (if (nil? (:current-vote r)) 0 1)))]
    (reduce reduce-fn 0 players)))

(defn mission-ready? []
  "Return whether or not the requisite votes have been placed."
  (let [current-mission (first (:missions @game-state))
        mission-count (first current-mission)
        votes-cast (votes-cast)]
    (= votes-cast mission-count)))

(defn cast-vote [name vote]
  "Cast a user's vote."
  (in-state-sync
   :voting
   (when (and (valid-vote? vote)
                  (is-on-team? name)
                  (nil? (get-player-vote name)))
     (alter game-state
            conj
            {:players (update-player name {:current-vote vote})})
     (if (mission-ready?)
       :mission-ready
       :vote-cast))))

(def evaluate-mission []
  "Determines who won the mission and updates state accordingly."
  (in-state-sync
   :mission-ready
   (let [players (:players @game-state)
         num-players (count-players)
         [_ negs-required] (first (:missions @game-state))
         remaining-missions (rest (:missions @game-state))
         votes (filter #(string? (:current-vote %)) players)
         vote-reduce-fn (fn [[for against] vote]
                          (if (= vote "for")
                            [(inc for) against]
                            [for (inc against)]))
         [for against] (reduce vote-reduce-fn votes)
         score (:score @game-state)
         winner (if (>= against negs-required)
                  :spies
                  :resistance)
         new-score (update-in score [winner] inc)
         leader (:leader @game-state)
         new-leader (mod (inc leader) num-players)]
     (alter game-state conj {:missions remaining-missions
                             :score new-score
                             :state :pick-team
                             :leader new-leader})
     winner)))
