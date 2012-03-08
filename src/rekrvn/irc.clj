(ns rekrvn.irc
  (:import (java.net Socket)
           (java.lang Thread)
           (java.io PrintWriter InputStreamReader BufferedReader)))

(declare conn-handler)

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
        quit (fn [] (queueMsg "QUIT"))]

    ;; register with server
    (write (str "NICK " (:nick server)))
    (write (str "USER " (:nick server) " 0 * :" (:name server)))


    ;; test the queue
    (joinChan "#urkl")
    ;; end of testing

    ;; loop until registered
    (let [accPattern (re-pattern (str "^:\\S+ \\d\\d\\d " (:nick server) " :"))]
      (while (nil? (re-find accPattern (.readLine (:in @conn))))
        ))
    ;; example successful string: ":flounder.dyndns.org 003 test :This Nov  4 2011"

    ;; handle messages normally
    (while
      (nil? (:exit @conn))
      (if (.ready (:in @conn))
        (let [msg (.readLine (:in @conn))]
          (println msg)
          (cond
            (re-find #"^ERROR :Closing Link:" msg) 
            (dosync (alter conn merge {:exit true}))

            (re-find #"^PING" msg)
            (write (str "PONG "  (re-find #":.*" msg)))

            (re-find #"!quit" msg)
            (quit)

            ;; (re-matches #"https?://twitter.com/#!/(.+)/status/(\d+)/?" msg)
            ;;   (message conn "#room" "handler code goes here")
            )))
      (if (not-empty @queue)
        (dosync (let [msg (first @queue)]
                  (write msg))
          (alter queue rest)))
      (Thread/sleep 100)
      ))
  )

