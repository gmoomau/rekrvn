(ns rekrvn.irc
  (:require rekrvn.core)
  (:import (java.net Socket)
           (java.lang Thread)
           (java.io PrintWriter InputStreamReader BufferedReader))
  )

(declare conn-handler)

(def triggers (ref []))

(defn addTrigger [t]
  (dosync
    (alter triggers conj t)))

(defn connect [server]
  (let [socket (Socket. (:server server) (:port server))
        in (BufferedReader. (InputStreamReader. (.getInputStream socket)))
        out (PrintWriter. (.getOutputStream socket))
        conn (ref {:in in :out out})]
    (doto (Thread. #(conn-handler conn server)) (.start))
    conn))


(defn conn-handler [conn server]
  ;; message queue
  (let [queue (ref [])
        queueMsg (fn [msg] (dosync (alter queue conj msg)))
        ;; irc commands
        write (fn [msg] (doto (:out @conn)
                          (.println (str msg "\r"))
                          (.flush)))

        joinChan (fn [channel] (queueMsg (str "JOIN " channel)))
        partChan (fn [channel] (queueMsg (str "PART " channel)))
        message (fn [recipient msg] (queueMsg (str "PRIVMSG " recipient " :" msg)))
        quit (fn [] (queueMsg "QUIT"))
        accPattern (re-pattern (str "^:\\S+ \\d\\d\\d " (:nick server) " :"))
        registered (ref false)]

    ;; register with server
    (write (str "NICK " (:nick server)))
    (write (str "USER " (:nick server) " 0 * :" (:name server)))


    (pmap joinChan (:channels server))


    ;; testing zone
    ;; end of testing


    ;; handle messages normally
    (while
      (nil? (:exit @conn))
      (while (or (not @registered) (.ready (:in @conn)))
        (let [msg (.readLine (:in @conn))]
          (println msg)
          (when (and (not @registered) (re-find accPattern msg))
            ;; example successful string: ":flounder.dyndns.org 003 test :This Nov  4 2011"
            (dosync (ref-set registered (ref true))))
          (cond
            (re-find #"^ERROR :Closing Link:" msg) 
            (dosync (alter conn merge {:exit true}))

            (re-find #"^PING" msg)
            (write (str "PONG "  (re-find #":.*" msg)))

            (re-find #"!quit" msg)
            (quit)
            )
          (let [recip (re-find #"PRIVMSG (\S+) :" msg)]
            (when @registered (rekrvn.core/broadcast {:id "irc" :content msg
                                                      :replyFn (partial message (second recip))}))
            )
          ))
      (while (and @registered (not-empty @queue))
        (dosync (let [msg (first @queue)]
                  (write msg))
          (alter queue rest)))
      (Thread/sleep 100)
      )
    ))

(def flounder {:serverId "flounder" :nick "cljr" :name "bottersworth" :server "flounder.dyndns.org" :port 6998 :channels '("#test")})
(connect flounder)
