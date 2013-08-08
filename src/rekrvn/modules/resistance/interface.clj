(ns rekrvn.modules.resistance.interface
  (:require [rekrvn.hub :as hub])
  (:require [clojure.string :as s])
  (:require [rekrvn.config :only resistance])
  (:require [rekrvn.modules.irc.client :as irc])
  (:require [rekrvn.modules.resistance.engine :as e]))

(def mod-name "resistance.interface")

;; chat stuff
(def irc-network (:network rekrvn.config/resistance))
(def irc-nick (:nick rekrvn.config/resistance))
(def irc-channel (:channel rekrvn.config/resistance))

(defn private-message [nick msg]
  (let [recip (if (= nick :broadcast) irc-channel nick)
        msg (str mod-name " forirc " irc-network "#" recip " " msg)]
    (hub/broadcast msg)))

;; game state
(def game-state (ref e/initial-game-state))

;; handlers
(defn handle-result [result]
  (doseq [[recip msg] (:messages result)]
    (private-message recip msg))
  (when (not (:error result))
    (dosync
      (ref-set game-state (assoc result :messages [])))))

(defn handle-join [[nick] _]
  ;; this is ugly buttttttttt
  (irc/modAllow irc-network nick mod-name)
  (handle-result (e/join-game @game-state nick)))

(defn handle-start [& _]
  (handle-result (e/start-game @game-state)))

(defn handle-choose-team [[player team-str] _]
  (let [team (->> team-str
               (s/trim)
               (#(s/split % #"\s+"))
               (reduce conj #{}))]
    (handle-result (e/pick-team @game-state player team))))

(defn handle-vote [[nick vote] _]
  (handle-result (e/vote @game-state nick vote)))

(def vote-pattern
  (re-pattern (str "^irc :(\\S+)!\\S+ PRIVMSG " irc-nick " :\\.rvote (pass|fail)")))

(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG \S+ :\.rjoin" handle-join)
(hub/addListener mod-name #"^irc.*PRIVMSG \S+ :\.rstart" handle-start)
(hub/addListener mod-name #"^irc :(\S+)!\S+ PRIVMSG \S+ :\.rteam (.+)" handle-choose-team)
(hub/addListener mod-name vote-pattern handle-vote)

