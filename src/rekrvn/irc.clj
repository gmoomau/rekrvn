(ns rekrvn.irc
  (:import (java.net Socket)
           (java.lang Thread)
           (java.io PrintWriter InputStreamReader BufferedReader)))

(declare conn-handler)

(def registered (ref false))

(defn write [conn msg]
  (doto (:out @conn)
       (.println (str msg "\r"))
       (.flush)))

(defn connect [server]
  (let [socket (Socket. (:server server) (:port server))
        in (BufferedReader. (InputStreamReader. (.getInputStream socket)))
        out (PrintWriter. (.getOutputStream socket))
        conn (ref {:in in :out out})]
    (doto (Thread. #(conn-handler conn server)) (.start))
    conn))

(defn joinChan [conn channel]
  (do
    (while (not @registered) (Thread/sleep 500))
    (write conn (str "JOIN " channel))))

(defn partChan [conn channel]
  (write conn (str "PART " channel)))

(defn message [conn channel msg]
  (do
    (while (not @registered) (Thread/sleep 500))
    (write conn (str "PRIVMSG " channel " :" msg))))

(defn quit [conn]
  (write conn "QUIT"))

(defn conn-handler [conn server]
  
  ;; register with server
  (write conn (str "NICK " (:nick server)))
  (write conn (str "USER " (:nick server) " 0 * :" (:name server)))

  ;; loop until registered
  (while (not @registered)
    (nil? (:exit @conn))
    (let [msg (.readLine (:in @conn))]
      (when (not (nil? (re-find (re-pattern (str "^:\\S+ \\d\\d\\d " (:nick server) " :")) msg)))
        ;; example successful string: ":flounder.dyndns.org 003 test :This server was created 15:50:17 Nov  4 2011"
        (dosync (ref-set registered true)))))

  ;; handle messages normally
  (while
    (nil? (:exit @conn))
    (let [msg (.readLine (:in @conn))]
      (cond
        ;;(re-(re-pattern (str ":(.+) \\d\\d\\d " (:nick user)))
        (re-find #"^ERROR :Closing Link:" msg) 
          (dosync (alter conn merge {:exit true}))
        (re-find #"^PING" msg)
          (write conn (str "PONG "  (re-find #":.*" msg)))
        (re-find #"!quit" msg)
            (quit conn)
        ;; (re-matches #"https?://twitter.com/#!/(.+)/status/(\d+)/?" msg)
        ;;   (message conn "#room" "handler code goes here")
      ))))

