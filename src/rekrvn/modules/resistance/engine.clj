(ns rekrvn.modules.resistance.engine
  (:require [clojure.string :as s]))

;; purely functional game engine
;; performs rule checking and validation
;; non-helper functions return an updated game-state

;;;;; Basic game constants
(def initial-game-state
  "The state of the game at startup."
  {:phase :inactive ; possible phases are :inactive, :pick-team, :voting, :mission-ready
   :players {} ; map of the names of players in the game
               ; contains: :faction, :vote, :is-on-team
   :player-order [] ; vector determining the order in which the leader advances
                    ; determined by join order
   :missions [] ; the missions remaining for this game
   :leader nil ; the name of the person picking the team for this mission
   :score {:resistance 0 ; first to 3 wins
           :spies 0}
   :messages [] ; queue of messages to deliver to the players
   })

(def error-message-table
  "Table of error messages in human form."
  {:wrong-phase "You can't perform that action at this time."
   :already-joined "You have already joined this game."
   :max-players "There are already ten players in the game."
   :not-enough-players "You need at least five players to start the game."
   :wrong-team-size "Your team is either too large or too small."
   :not-leader "You must be the leader to select a team."
   :not-in-mission "Only members of the current team can vote."
   :already-voted "You've already voted on this mission."
   :invalid-vote "Invalid vote. Must vote 'for' or 'against'."})

(def ^:private new-player
  {:faction nil :vote nil :is-on-team nil})

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
  {2  [2 2 2]
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
(defn assoc-error
  ([game-state reason]
     (assoc-error game-state reason :broadcast))
  ([game-state reason recipient]
     (conj game-state {:error reason
                       :messages [[recipient (reason error-message-table)]]})))

(defn- append-message [game-state recipient message]
  "Adds a message to the message queue."
  (update-in game-state [:messages] conj [recipient message]))

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
  (:is-on-team ((:players game-state) player-name)))

(defn- get-mission-team-sizes [num-players]
  (map expand-mission-team-info (get mission-team-sizes num-players)))

(defn- get-current-mission [game-state]
  (first (:missions game-state)))

(defn- valid-vote [vote]
  (let [vote-map {"pass" :pass "fail" :fail}]
    (get vote-map vote)))

(defn- get-votes [game-state]
  "Returns a list of all of the votes."
  (->> game-state
       :players
       (map (fn [[_ player-data]]
              (:vote player-data)))
       (filter identity)))


(defn- process-votes [game-state]
  "Takes in the game state and returns a vector of two items. The first
   item is the number of votes in favor and the second is the number
   of votes against."
  (let [votes (get-votes game-state)]
    (reduce (fn [[passes fails] vote]
              (if (= vote :pass)
                [(inc passes) fails]
                [passes (inc fails)]))
            [0 0]
            votes)))

(defn- get-winner [game-state]
  "Takes a game state and returns a keyword representing the faction which
   won the mission."
  (let [[_ votes-to-fail] (get-current-mission game-state)
        [_ fails] (process-votes game-state)]
    (if (>= fails votes-to-fail)
      :spies
      :resistance)))

(defn- update-score [game-state]
  "Calculates the winner and updates the score accordingly."
  (let [winner (get-winner game-state)]
    (-> game-state
        (update-in [:score winner] inc)
        (append-message :broadcast (str "The " (name winner) " won the round!")))))

(defn- reset-players [game-state]
  "Resets the players votes and team assignment."
  (let [players (:players game-state)
        names (keys players)
        player-updates (zipmap names (repeat {:vote nil :is-on-team nil}))
        updated-players (merge-with merge players player-updates)]
    (assoc game-state :players updated-players)))

(defn- choose-next-leader [game-state]
  "Advances the leader."
  (let [player-order (:player-order game-state)
        num-players (count player-order)
        leader (:leader game-state)
        current-idx (.indexOf player-order leader)
        new-idx (mod (inc current-idx) num-players)
        new-leader (nth player-order new-idx)]
    (assoc game-state :leader new-leader)))

(defn- add-mission-message [game-state]
  (if (empty? (:missions game-state))
    game-state
    (let [[num-players to-fail] (get-current-mission game-state)
          mission-num (inc (- 5 (count (:missions game-state))))
          mission-str (str "The current mission ("
                           mission-num
                           ") is lead by "
                           (:leader game-state)
                           " and requires "
                           num-players
                           " players and "
                           to-fail
                           " negative vote(s) to fail.")]
      (append-message game-state :broadcast mission-str))))

(defn- advance-mission [game-state]
  "Removes the recently completed mission."
  (-> game-state
      (update-in [:missions] rest)
      (add-mission-message)))

(defn- winning-faction-str [score]
  (if (> (:resistance score) (:spies score))
    "The resistance has won the game!"
    "The spies have won the game!"))

(defn- update-game-status [game-state]
  "Determines if either faction has won and updates the phase accordingly."
  (let [score (:score game-state)
        resistance-score (:resistance score)
        spies-score (:spies score)]
    (if (or (>= resistance-score 3)
            (>= spies-score 3))
      (append-message initial-game-state
                      :broadcast
                      (winning-faction-str score))
      (assoc game-state :phase :pick-team))))

(defn- evaluate-mission [game-state]
  "Determines the outcome of a mission. Advances if necessary, otherwise
   it will end the game."
  (-> game-state
      (update-score) ; reads votes, updates score accordingly
      (reset-players) ; resets player votes & team status
      (choose-next-leader) ; choose the next leader
      (advance-mission) ; pops the last mission off the mission queue
      (update-game-status))) ; checks if the game is completed

(defn- evaluate-mission-if-necessary [game-state]
  "Will evaluate the success of the mission if all votes have been cast."
  (let [[votes-required _] (get-current-mission game-state)
        votes (get-votes game-state)
        num-votes (count votes)]
    (if (= votes-required num-votes)
      (evaluate-mission game-state)
      game-state)))

(defn- cast-vote [game-state player vote]
  "Sets a users vote."
  (-> game-state
      (update-in [:players player] assoc :vote vote)
      (append-message player "Vote cast.")
      (evaluate-mission-if-necessary)))

(defn- leader? [game-state player-name]
  (= player-name (:leader game-state)))

(defn- spy? [game-state player-name]
  (= :spies (get-in game-state [:players player-name :faction])))

(defn- resistance? [game-state player-name]
  (= :resistance (get-in game-state [:players player-name :faction])))

(defn- assign-factions [players]
  "Assigns players to different factions at the start of the game."
  (let [num-players (count players)
        faction-split (get faction-balances num-players)
        factions (shuffle (gen-factions faction-split))
        names (keys players)]
    (zipmap names (map #(do {:faction %}) factions))))

(defn- get-spies [game-state]
  (let [players (:players game-state)
        names (keys players)]
    (filter #(= :spies (:faction (get players %))) names)))

(defn- get-faction-notification [game-state player-name]
  (cond
    (resistance? game-state player-name) "You are on the resistance."
    (spy? game-state player-name) (let [spy-list (get-spies game-state)
                                        other-spies (remove #(= player-name %) spy-list)
                                        spy-str (s/join " " other-spies)]
                         (str "You are a spy. Your fellow spies are: " spy-str))))

(defn- add-faction-notifications [game-state]
  (let [players (:players game-state)
        player-names (keys players)
        faction-strs (map #(get-faction-notification game-state %) player-names)
        msg-vecs (map vector player-names faction-strs)]
    (update-in game-state [:messages] concat msg-vecs)))

(defn- add-mission-message [game-state]
  (let [[num-players to-fail] (get-current-mission game-state)
        mission-num (inc (- 5 (count (:missions game-state))))
        mission-str (str "The current mission ("
                         mission-num
                         ") is lead by "
                         (:leader game-state)
                         " and requires "
                         num-players
                         " players and "
                         to-fail
                         " negative vote(s) to fail.")]
    (append-message game-state :broadcast mission-str)))

(defn- add-player-to-game [game-state player-name]
  "Adds a new player to the game state."
  (-> game-state
      (assoc-in [:players player-name] new-player)
      (update-in [:player-order] conj player-name)))

(defn- add-new-player-message [game-state player-name]
  (let [players (:players game-state)
        names (keys players)
        names-str (s/join ", " names)]
    (append-message game-state
                    :broadcast
                    (str player-name
                         " joined the game. ["
                         names-str
                         "]"))))

;;;;; Public-facing game logic
    ; everything in this section either returns the new
    ; game state or a map with an :error code describing
    ; the reason that the function failed

(defn join-game [game-state player-name]
  "<player-name> attempts to join the game."
  (in-phase
    game-state :inactive
    (if (< (num-players game-state) 10)
      (if (not (is-playing? game-state player-name))
        (-> game-state
            (add-player-to-game player-name)
            (add-new-player-message player-name))
        (assoc-error game-state :already-joined))
      (assoc-error game-state :max-players))))

(defn start-game [game-state]
  "Joining has ended. Start the first mission."
  (in-phase
   game-state :inactive
   (let [players (:players game-state)
         num-players (count players)]
     (if (>= num-players 2)
       (let [faction-assignments (assign-factions (:players game-state))
             new-state-partial {:players (merge-with merge (:players game-state) faction-assignments)
                                :missions (get-mission-team-sizes (count players))
                                :phase :pick-team
                                :leader (nth (keys players) (rand-int num-players))}
             new-state (conj game-state new-state-partial)]
         (-> new-state
           add-mission-message
           add-faction-notifications))
       (assoc-error game-state :not-enough-players)))))


(defn pick-team [game-state player-name team]
  "Player <player> attempts to choose the team <team> for the mission."
  (in-phase
   game-state :pick-team
   (if (leader? game-state player-name)
     (if (= (first (get-current-mission game-state)) (count team))
       (when (every? (partial is-playing? game-state) team)
         (let [new-state (reduce (fn [memo team-member]
                                   (assoc-in memo [:players team-member :is-on-team] true))
                                 game-state
                                 team)]
           (-> new-state
               (append-message :broadcast "Team selected. Go forth and vote!")
               (conj {:phase :voting}))))
       (assoc-error game-state :wrong-team-size))
     (assoc-error game-state :not-leader))))

(defn vote [game-state player choice]
  "Player <player> attempts to vote <choice>."
  (in-phase
    game-state :voting
    (if-let [vote (valid-vote choice)]
      (if (not (get-in game-state [:players player :vote]))
        (if (get-in game-state [:players player :is-on-team])
          (cast-vote game-state player vote)
          (assoc-error game-state :not-in-mission))
        (assoc-error game-state :already-voted))
      (assoc-error game-state :invalid-vote))))
