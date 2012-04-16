(ns rekrvn.irc
  (:require rekrvn.core)
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
(defn quit [conn] (queueMsg conn "QUIT"))

(defn permits [recip module]
  ;; add handling for no permissions specified
  ;; make up new ones/add from servers map
  (when-let [permSet (get @modPerms recip)]
    (if (:defaultAllow permSet) ;; deny by default unless config says otherwise
      (not (contains? (:blacklist permSet) module)) ;; check blacklist
      (contains? (:whitelist permSet) module) ;; if default deny, check whitelist
      )
    ))

(defn doSomething [[fromModule network recip msg] replyFn]
  ;; for now only support (message), add other stuff later
  (when (and (permits (str network "#" recip) fromModule)
             (contains? @connections network))
    (println fromModule recip (permits (str network "#" recip) fromModule))
    (message (get @connections network) recip msg)
    ))

(defn shutdown []
  (do
    (doall (map (vals connections) quit))
    (dosync
      (ref-set servers {})
      (ref-set connections {})
      (ref-set modPerms {})
      (ref-set currentChannels #{}))
    ))

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
        write (fn [msg] (doto (:out @conn)
                          (.println (str msg "\r"))
                          (.flush)))]
    ;; register with server
    (write (str "NICK " (:nick server)))
    (write (str "USER " (:nick server) " 0 * :" (:realname server)))

    (doall (map (partial joinChan conn) (:channels server)))
    ;; testing stuff goes here?
    ;; testing stuff ends here

    ;; handle messages
    (while
      (nil? (:exit @conn))
      (while (or (not @registered) (.ready (:in @conn)))
        (let [msg (.readLine (:in @conn))]
          (println "fromirc" msg)
          (when (and (not @registered) (re-find (re-pattern (str serverMsg " :")) msg))
            (dosync (ref-set registered (ref true))))

          ;; maintaining connection + internal irc state
          (when (re-find #"^ERROR :Closing Link:" msg)
            (dosync
              (alter conn merge {:exit true})
              (alter connections dissoc network)
              ))

          (when (re-find #"^PING" msg)
            (write (str "PONG " (re-find #":.*" msg))))

          (when (re-find #"!quit" msg)
            (quit conn))

          (when-let [joined (re-find (re-pattern (str serverMsg " = (\\S+) :")) msg)]
            (dosync (alter currentChannels conj (str network "#" (second joined)))))
          (when-let [kicked (re-find #"\S+ KICK (#\S+) " msg)]
            ;; rejoin functionality goes here
            (dosync (alter currentChannels disj (str network "#" (second kicked)))))

          ;; normal chat
          (when-let [recip (re-find #"PRIVMSG (\S+) :" msg)]
            (let [reply (fn [modName msg]
                          (doSomething [modName network (second recip) msg] nil))]
            (when @registered
              (rekrvn.core/broadcast (str "irc " msg) reply))
            ))
          ))
      (when (and @registered (not-empty (:queue @conn)))
        (doall (map write (:queue @conn)))
        (dosync (alter conn assoc :queue []))
        )
      (Thread/sleep 100)
      )
    ))

(defn startirc []
  (let [srv '({:network "flounder" :nick "cljr" :realname "bot" :autoConnect true
               :server "flounder.dyndns.org" :port 6998 :channels ["#test"]
               :perms [{:channel "#test" :defaultAllow true :whitelist #{"twurl"} :blacklist #{}}
                       {:channel "#room" :defaultAllow false :whitelist #{}}
                        ]
               }
              )]
    (doall (map (fn [server]
                  (dosync
                    ;; add server to internal list for some reason
                    (alter servers assoc (:network server) server)
                    ;; save all channel permission
                    (doall (map (fn [perm]
                                  (alter modPerms assoc (str (:network server) "#" (:channel perm)) perm))
                                (:perms server)
                                ))
                    )
                  ;; connect to server if specified
                  (when (:autoConnect server) (connect server))
                  )
                srv))
    ))


;; definitions done, actually doing stuff now
(rekrvn.core/addListener #"^(\S+) forirc (\S+)#(\S+) (.+)" doSomething)
(rekrvn.core/addCleanup modName shutdown)
;; read from config file
(startirc)

