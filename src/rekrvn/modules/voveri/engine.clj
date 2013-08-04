(ns rekrvn.modules.voveri.engine)

;; purely functional game engine
;; performs rule checking and validation
;; non-helper functions return an updated game-state

;;;;; Basic game constants
(def initial-game-state
  "The state of the game at startup."
  {:phase :inactive ; possible phases are :inactive, :pick-team, :voting, :mission-ready
   :players #{} ; set of the names of players in the game
   :missions [] ; the missions remaining for this game
   :leader nil ; the one picking the team for this mission
   :team [] ; the group of players voting in the current mission
   :score {:resistance 0 ; first to 3 wins
           :spies 0}})

;; Faction balancing
(defn- gen-factions [[r s]]
  "Generate faction designations with <r> resistance members and <s> spies."
  (concat
    (repeat r :resistance)
    (repeat s :spies)))

(def ^:private faction-balances
  "The split of resistance members vs spies for different game sizes."
  {2  [2 0]
   5  [3 2]
   6  [4 2]
   7  [4 3]
   8  [5 3]
   9  [6 3]
   10 [6 4]})

;; Mission rules
(def ^:private mission-team-sizes
  "The mission info. Each entry in the vector corresponds
   to the number of players required in each mission. If
   the entry is a number, the mission requires one (default)
   failing vote to fail. If the entry is a vector, the first
   item is the numbers of voters and the second item is the
   number of failing votes required to fail the mission."
  {2  [2 2]
   5  [2 3 2 3 3]
   6  [2 3 4 3 4]
   7  [2 3 3 [4 2] 4]
   8  [3 4 4 [5 2] 5]
   9  [3 4 4 [5 2] 5]
   10 [3 4 4 [5 2] 5]})

(defn- expand-mission-team-info [info]
  "Expands the mission team info based on the above protocol
   for defining team sizes."
  (cond
    (number? info) [info 1]
    (vector? info) info))

;;;;; Utility
;;;;;
(defn assoc-error [game-state reason]
  (conj game-state {:error reason}))

(defmacro in-phase [game-state phase & forms]
  "When the game is in the given phase, evaluate the forms.
   Otherwise, return nil."
  `(if (= (:phase ~game-state) ~phase)
     (do ~@forms)
     (assoc-error ~game-state :wrong-phase)))

(defn- num-players [game-state]
  (count (:players game-state)))

(defn- is-playing? [game-state player-name]
  ((:players game-state) player-name))

(defn- is-voting? [game-state player-name]
  ((:current-team game-state) player-name))

(defn- get-mission-team-sizes [num-players]
  (map expand-mission-team-info (get mission-team-sizes num-players)))

(defn- get-current-mission [game-state]
  (first (:missions game-state)))

(defn- valid-vote? [vote]
  (#{"for" "against"} vote))

(defn- leader? [game-state player-name]
  (= player-name (:name (:leader game-state))))

;;;;; Public-facing game logic
    ; everything in this section either returns the new
    ; game state or a map with an :error code describing
    ; the reason that the function failed
(defn join-game [game-state player-name]
  "<player-name> attempts to join the game."
  (in-phase
    game-state :inactive
    (if (< (num-players game-state) 10)
      (if (not (is-playing? player-name))
        (update-in game-state [:players] conj player-name)
        (assoc-error game-state :already-joined))
      (assoc-error game-state :max-players))))

(defn start-game [game-state]
  "Voting has ended. Start the first mission."
  (in-phase
    game-state :inactive
    (let [players (:players game-state)
          num-players (count players)]
      (if (>= num-players 2)
        (let [new-state {:players (:players game-state)
                         :missions (get-mission-team-sizes (count players))
                         :phase :pick-team
                         :leader (nth (seq players) (rand-int num-players))}]
          new-state)
        (assoc-error game-state :not-enough-players)))))

(defn pick-team [game-state player-name team]
  "Player <player> attempts to choose the team <team> for the mission."
  (in-phase
    game-state :pick-team
    (if (leader? game-state player-name)
      (if (= (first (get-current-mission game-state)) (count team))
        (when (every? (partial is-playing? game-state) team)
          (conj game-state
                {:current-team team
                 :phase :voting}))
        (assoc-error game-state :wrong-team-size))
      (assoc-error game-state :not-leader))))

(defn vote [game-state player choice]
  "Player <player> attempts to vote <choice>."
  ;;(if (is-voting?
  )


