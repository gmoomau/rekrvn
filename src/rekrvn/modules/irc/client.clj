(ns rekrvn.modules.irc.client
  (:require [rekrvn.hub :as hub])
  (:use [rekrvn.config :only [irc-opts]])
  (:import (java.net Socket)
           (java.lang Thread)
           (java.io PrintWriter InputStreamReader BufferedReader File))
  )

(def modName "irc")

;; state variables
(def servers (ref {})) ;; id->{} (read from config file)
(def connections (ref {})) ;; id->conn (changes in real time)
(def modPerms (ref {})) ;; id#channel->{} (default from @servers, changes in real time)
(def currentChannels (ref #{})) ;; id#channel (changes in real time)

;; irc commands
(defn queueMsg [conn msg]
  (dosync (alter conn assoc :queue (conj (:queue @conn) msg))))
(defn joinChan [conn channel] (queueMsg conn (str "JOIN " channel)))
(defn partChan [conn channel] (queueMsg conn (str "PART " channel)))
(defn message [conn recipient msg] (queueMsg conn (str "PRIVMSG " recipient " :" msg)))
;; add handling in (message) for when you're not in a channel?
(defn quit [conn]
  (dosync
    (alter conn assoc :exit true)))

(defn write [conn msg]
  (doto (:out @conn)
    (.println (str msg "\r"))
    (.flush)))

(defn raw [[server cmd] replyFn]
  (when-let [conn (get @connections server)]
    (write conn cmd)))

(defn permits [recip module]
  ;; add handling for no permissions specified
  ;; make up new ones/add from servers map
  (when-let [permSet (get @modPerms recip)]
    (if (:defaultAllow permSet) ;; deny by default unless config says otherwise
      (not (contains? (:blacklist permSet) module)) ;; check blacklist
      (contains? (:whitelist permSet) module) ;; if default deny, check whitelist
      )))

(defn modAllow [network channel module]
  (let [recip (str network "#" channel)
        permSet (get @modPerms recip)]
    (if permSet
      (dosync
        (alter modPerms update-in [recip :whitelist] conj module)
        (alter modPerms update-in [recip :blacklist] disj module))
      (dosync (alter modPerms
                     assoc recip {:defaultAllow false :blacklist #{} :whitelist #{module}})))))

(defn modDeny [network channel module]
  (let [recip (str network "#" channel)
        permSet (get @modPerms recip)]
    (if permSet
      (dosync
        (alter modPerms update-in [recip :blacklist] conj module)
        (alter modPerms update-in [recip :whitelist] disj module))
      (dosync (alter modPerms
                     assoc recip {:defaultAllow false :whitelist #{} :blacklist #{module}})))))

(defn doSomething [[fromModule network recip msg] replyFn]
  ;; for now only support (message), add other stuff later
  (when (and (permits (str network "#" recip) fromModule)
             (contains? @connections network))
    (message (get @connections network) recip msg)))

(defn shutdown []
  (doseq [conn @connections] (quit conn)))

(declare conn-handler)
(defn connect [server]
  (let [socket (Socket. (:server server) (:port server))
        in (BufferedReader. (InputStreamReader. (.getInputStream socket)))
        out (PrintWriter. (.getOutputStream socket))
        conn (ref {:in in :out out :queue []})]
    (dosync (alter connections assoc (:network server) conn))
    (doto (Thread. #(conn-handler conn server)) (.start))
    conn))

(defn conn-handler [conn server]
  (let [serverMsg (str "^:\\S+ \\d\\d\\d " (:nick server))
        registered (ref false)
        network (:network server)
        register (fn [nick]
                   (write conn (str "NICK " nick))
                   (write conn (str "USER " nick " 0 * :" (:realname server))))
        ]
    ;; register with server
    (register (:nick server))

    (doseq [chan (:channels server)] (joinChan conn chan))

    ;; handle messages
    (while (not (:exit @conn))
      (while (or (not @registered) (.ready (:in @conn)))
        (let [msg (.readLine (:in @conn))]
          (println "fromirc" msg)
          (when (and (not @registered) (re-find (re-pattern (str serverMsg " :")) msg))
            (dosync (ref-set registered (ref true))))

          ;; maintaining connection + internal irc state
          (when (re-find #"^ERROR :Closing Link:" msg)
            (quit conn))

          (when (re-find #"^PING" msg)
            (write conn (str "PONG " (re-find #":.*" msg))))

          (when @registered
            ;; ugly hacks below
            (when-let [rawcmd (re-find #"^:grog\S+ PRIVMSG #dump :\.raw (\S+) (.*)" msg)]
              (raw (rest rawcmd) (fn [& args])))
            ;; slightly less ugly hacks below
            (when (re-find #"127.0.0.1.*!quit" msg)
              (quit conn))

            ;; channel management
            (when-let [joined (re-find (re-pattern (str serverMsg " = (\\S+) :")) msg)]
              (dosync (alter currentChannels conj (str network "#" (second joined)))))
            (when-let [kicked (re-find #"\S+ KICK (#\S+) " msg)]
              ;; current regex can be faked
              ;; rejoin functionality goes here
              (dosync (alter currentChannels disj (str network "#" (second kicked)))))

            (when-let
              [invited (re-find (re-pattern (str "INVITE " (:nick server) " :(#\\S+)$")) msg)]
              (joinChan conn (second invited)))

            ; module management
            (when-let [cmd (re-find #"PRIVMSG (\S+) :\.(en)?(dis)?able (\S+)$" msg)]
              (let [[source enable disable module] (rest cmd)]
                (cond
                  enable (modAllow network source module)
                  disable (modDeny network source module))))

            ;; irc stuff that happens. this should/will all be changed soon
            (if-let [recip (re-find #"PRIVMSG (\S+) :" msg)]
              ;; privmsgs have replyfns
              (let [reply (fn [modName msg]
                            (doSomething [modName network (second recip) msg] nil))
                    filter-fn (fn [mod-name]
                                (let [my-recip (str network "#" recip)]
                                  (permits my-recip mod-name)))]
                (hub/broadcast (str "irc " msg) reply filter-fn))
              ;; otherwise don't
              (hub/broadcast (str "irc " msg))))))

      (when (and @registered (not-empty (:queue @conn)))
        (doseq [msg (:queue @conn)] (write conn msg))
        (dosync (alter conn assoc :queue [])))
      (Thread/sleep 100))

    ;; only gets here when it receives the exit command
    (dosync
      (write conn "QUIT")
      (ref-set currentChannels (remove (fn [chan] (re-find (re-pattern (str "^" network "#")) chan)) @currentChannels))
      (alter connections dissoc network))))

(defn startirc []
  (doseq [server irc-opts]
    (dosync
      ;; add server to internal list for some reason
      (alter servers assoc (:network server) server)
      ;; load permissions
      (doseq [perm (:perms server)]
        (let [emptyPerms {:defaultAllow false :blacklist #{} :whitelist #{}}]
          (alter modPerms
                 assoc (str (:network server) "#" (:channel perm)) (merge emptyPerms perm)))))
    ;; connect to server if specified
    (when (:autoConnect server) (connect server)))
  )


;; definitions done, actually doing stuff now
(hub/addListener "irc.client" #"^(\S+) forirc (\S+)#(\S+) (.+)" doSomething)
(hub/addListener "irc.client" #"^\S+ irccmd (\S+) (.+)" raw)
;; initialize options from config file
(startirc)
