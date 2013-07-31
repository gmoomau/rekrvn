(ns rekrvn.modules.resistance
  (:require [rekrvn.hub :as hub])
  (:require [clojure.string :as s])
  (:require [rekrvn.config :as c])
  (:require [rekrvn.modules.irc.client :as irc]))

(def mod-name "resistance")

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

(defn gen-teams [r s]
  "Generate a team with <r> resistance members and
   <s> spies."
  (concat
   (repeat r :resistance)
   (repeat s :spies)))

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

(defn is-player? [name]
  "Verify the existence of a player with the name <name>"
  (let [players (:players @game-state)]
    (some #(= (:name %) name) players)))

(def resistance-network (:network c/resistance-config))
(def resistance-nick (:nick c/resistance-config))
(def resistance-channel (:channel c/resistance-config))

(defn enable-user-messages [name]
  (irc/modAllow resistance-network name mod-name))

(defn disable-user-messages [name]
  (irc/modDeny resistance-network name mod-name))

(defn join-game [name]
  "Adds a user to the game if the game has not started.
   Returns true on success and nil on failure."
  (in-state-sync
   :inactive
   (let [players (:players @game-state)
         new-players (cons {:name name
                            :team nil
                            :current-vote nil}
                           players)]
     (if (< (count players) 10)
       (if (not (is-player? name))
         (do
           (alter game-state conj {:players new-players})
           (enable-user-messages name)
           :player-joined)
         :already-joined)
       :max-players))))

(declare private-message)
(defn send-team-message [player]
  (let [name (:name player)
        team (:team player)]
    (private-message name (str "You're on the " team))))

(defn start-game []
  "Initializes a game. Sets state to active, chooses teams,
   notifies the players of their teams and starts the game."
  (in-state-sync
   :inactive
   (let [players (:players @game-state)
         num-players (count players)]
     (if (>= num-players 5)
       (let [new-state {:players (set-teams players)
                        :missions (get-mission-team-sizes num-players)
                        :state :pick-team
                        :leader (rand-int num-players)}]
         (alter game-state conj new-state)
         (doseq [p (:players new-state)]
           (send-team-message p))
         :game-started)
       :not-enough-players))))

(defn is-on-team? [name]
  "Verify <name> is on the current name."
  (let [team (:current-team @game-state)]
    (some #(= name (:name %)) team)))

(defn get-player [name]
  "Retrieve the given player from the list."
  (let [players (:players @game-state)]
    (some #(= (:name %) name) players)))

(defn leader? [name game-state]
  (let [players (:players game-state)
        leader-idx (:leader game-state)
        leader (nth players leader-idx)]
    (= (:name leader) name)))

(defn pick-team [name team]
  "Allow the current leader to choose their team."
  (in-state-sync
   :pick-team
   (if (leader? name @game-state)
     (when (every? is-player? team)
       (alter game-state
              conj
              {:current-team team
               :state :voting})
       :team-ready)
     :not-leader)))

(defn update-player [name vals]
  "Update the player with the values in vals."
  (let [players (:players @game-state)]
    (map (fn [p]
           (if (= name (:name p))
             (conj p vals)
             p)))))

(defn valid-vote? [vote]
  "Verify the vote is for or against."
  (#{"for" "against"} vote))

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

(defn get-player-vote [name]
  (let [player (get-player name)]
    (:current-vote player)))

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

(defn tabulate-votes [[for against] vote]
  (if (= vote "for")
    [(inc for) against]
    [for (inc against)]))

(defn erase-votes [players]
  (map #(conj % {:current-vote nil}) players))

(defn game-over? [game-state]
  (let [score (:score game-state)
        resistance-score (:resistance score)
        spies-score (:spies score)]
    (or (when (>= spies-score 3) :spies)
        (when (>= resistance-score 3) :resistance))))

(defn unregister-players [players]
  (doseq [p players]
    (disable-user-messages (:name p)))
  [])

(defn evaluate-mission-helper [game-state]
  (let [players (:players game-state)
        num-players (count players)
        [_ negs-required] (first (:missions game-state))
        remaining-missions (rest (:missions game-state))
        votes (map :current-vote
                   (filter #(string? (:current-vote %)) players))
        [pass against] (reduce tabulate-votes [0 0] votes)
        score (:score game-state)
        winner (if (>= against negs-required)
                 :spies
                 :resistance)
        new-score (update-in score [winner] inc)
        leader (:leader game-state)
        new-leader (mod (inc leader) num-players)
        new-state (if (game-over? game-state)
                    :inactive
                    :pick-team)
        new-players (if (= new-state :inactive)
                      (unregister-players players)
                      (erase-votes players))]
    [[winner [pass against]] {:missions remaining-missions
             :score new-score
             :state new-state
             :leader new-leader
             :players new-players}]))

(def sample-state-end-of-mission
  {:state :mission-ready
   :missions [[4 1]]
   :players [{:name "foo"
              :current-vote "for"}
             {:name "bar"
              :current-vote "for"}
             {:name "baz"
              :current-vote "against"}
             {:name "qux"
              :current-vote "for"}]
   :score {:resistance 1
           :spies 0}
   :leader 3})

(defn evaluate-mission []
  "Determines who won the mission and updates state accordingly."
  (in-state-sync
   :mission-ready
   (let [[winner updates] (evaluate-mission-helper @game-state)]
     (alter game-state conj updates)
     winner)))

(defn handle-join [[name] reply]
  (let [result (join-game name)]
    (cond
     (= result :player-joined) (dosync
                                (let [players (:players @game-state)
                                      names (map :name players)
                                      name-str (s/join ", " names)
                                      response (str name " joined the game. [" name-str "]")]
                                  (reply mod-name response)))
     (= result :already-joined) (reply mod-name "Player already joined.")
     (= result :max-players) (reply mod-name "Too many players already.")
     :else (reply mod-name "Game active. Wait until the game is over to join."))))

(defn resistance? [player]
  (= (:team player) :resistance))

(defn spy? [player]
  (= (:team player) :spies))

(defn private-message [nick msg]
  (let [msg (str mod-name " forirc " resistance-network "#" nick " " msg)]
    (hub/broadcast msg)))

(defn initial-game-message [reply]
  (reply mod-name "Starting the game. Your team will be sent to you in a private message.")
  (dosync
   (let [players (:players @game-state)
         leader-index (:leader @game-state)
         leader (:name (nth players leader-index))
         resistance (filter resistance? players)
         spies (filter spy? players)
         spies-str (fn [s]
                     (let [cohort (filter #(not (= (:name %) s)) spies)]
                       (s/join ", " cohort)))]
     (doseq [r resistance]
       (private-message r "You are on the resistance."))
     (doseq [s spies]
       (private-message s (str "You are a spy. Fellow spies are " (spies-str s) ".")))
     (reply mod-name (str "Player to start is " leader ". Choose a team.")))))

(defn handle-start [_ reply]
  (let [result (start-game)]
    (cond
     (= result :game-started) (initial-game-message reply)
     (= result :not-enough-players) (reply mod-name "You need a minimum of 5 players (and maximum of 10) to start.")
     :else (reply mod-name "Game already started."))))

(defn current-leader [game-state]
  (let [players (:players game-state)
        leader-idx (:leader game-state)]
    (nth players leader-idx)))

(defn start-mission [reply]
  (let [missions (:missions @game-state)
        mission-num (inc (- 5 (count missions)))
        [num-players win-threshold] (first missions)
        votes-to-win (- num-players win-threshold)
        mission-str (str "Mission " mission-num " requires " num-players " players and " votes-to-win " votes to pass.")
        team (:current-team @game-state)
        team-str (str "The team for this mission is: " (s/join ", " team) ".")]
    (reply mod-name (s/join " " [mission-str team-str "Deliberate! Be scandalous! Vote!"]))))

(defn handle-choose-team [[name team-str] reply]
  (let [team (-> team-str
                 (s/trim)
                 (#(s/split % #" ")))
        leader (current-leader @game-state)
        leader-name (:name leader)
        result (pick-team name team)]
    (cond (= result :team-ready) (start-mission reply)
          (= result :not-leader) (reply mod-name (str "The leader (" leader-name ") must choose the team."))
          :else (reply mod-name "Invalid game state. The team must be chosen at the beginning of a round."))))

(defn handle-vote [[name vote] reply]
  (let [result (cast-vote name vote)]
    (cond (= result :mission-ready) (let [[winner [pass fail]] evaluate-mission
                                          winner-str (if (= winner :resistance) "resistance" "spies")
                                          winner-line (str "The " winner-str " won the round " pass " passes to " fail " fails!")]
                                      (private-message resistance-channel winner-line))
          (= result :vote-cast) (private-message name "Vote cast.")
          :else (private-message name "You must be part of an active team to vote."))))

(def vote-pattern
  (re-pattern (str "^irc :(\\S+)!\\S+ PRIVMSG " resistance-nick " :\\.rvote (pass|fail)")))

(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG \S+ :\.rjoin" handle-join)
(hub/addListener mod-name #"^irc.*PRIVMSG \S+ :\.rstart" handle-start)
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG \S+ :\.rteam (.+)" handle-choose-team)
(hub/addListener mod-name vote-pattern handle-vote)
