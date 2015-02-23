(ns minimap.imap
  (:require [minimap.parse :as parse]
            [minimap.headers :as h]
            [ring.util.codec :as codec])
  (:import [java.io BufferedWriter BufferedReader OutputStreamWriter InputStreamReader]
           [javax.net.ssl SSLSocket SSLSocketFactory]))

(defn log [author msg]
  (spit "imap.log"
        (format "%s %s\n" (if (= :server author) "<<" ">>") msg)
        :append true))

(defn do-command
  "Executes an IMAP command in the context of a session. 
  
  - If successful, returns a future on a list of IMAP response lines.
  - Otherwise, set :last-error in the session and returns a future on nil.
  
  If the session has an error in the previous command, just pass through
  (this enables to chain commands without worrying about error at each step).
  
  Modifies the value of the session referenced by session-ref."
  [session-ref command]
  (let [{:keys [w r idx last-error] :as session} (deref session-ref)]
    (if-not last-error
      (let [idx (inc idx)
            id (format "a%05d" idx)
            cmd (format "%s %s\r\n" id command)
            response (fn [resp error] (dosync (alter session-ref
                                               assoc :idx idx
                                                     :last-error error))
                                      resp)]
        (.write w cmd 0 (count cmd))
        (log :client cmd)
        (.flush w)
        (future
          (loop [line (.readLine r)
                 resp []]
            (log :server line)
            ; it can be either the end "a00001 OK bla bla" or a response "* ..."
            (cond 
              ; OK end of response
              (re-find (re-pattern (str "^" id " OK")) line)
              (response resp nil)
                
              ; BAD end of response: everything with id but no OK
              (re-find (re-pattern (str "^" id)) line)
              (response nil (format "IMAP error in command %s: %s" command line))
                
              ; response line (starts with *)
              (.startsWith line "*")
              (if-let [[_ g] (re-find #"\{(\d+)\}$" line)]
                ; we have a literal like {67896}
                (let [size (Integer/parseInt g)
                      b (char-array size)]
                  ; read data bytes directly
                  (loop [i 0]
                    ;(log :server (format "Read %d bytes out of %d" i size))
                    (when (< i size)
                      (recur (+ i (.read r b i (- size i)))))) 
                  ; 
                  (recur (.readLine r) (-> resp (conj [line, b]))))
                
                ; self-contained response line, no data following
                (recur (.readLine r) (conj resp [line])))
                
              ; continuation request (starts with +)
              ; typically used during authentication, but not on Gmail
              ; for Gmail, just reply with an empty command and continue reading
              (.startsWith line "+")
              (do
                (.write w "\r\n" 0 2) (log :client "") (.flush w)
                (recur (.readLine r) (conj resp [line])))
              
              ; else: it's more of the current response (like after literals)
              :else
              (recur (.readLine r) (conj (pop resp) (conj (last resp) line)))))))
              
        (future nil)))) ; error pass-through: leave session untouched
          
(defn search
  "Returns a list of uids (strings)"
  [sess {:keys [gmail] :as query}]
  (let [_    (deref (do-command sess "select \"[Gmail]/All Mail\""))
        resp (deref (do-command sess (if gmail (format "SEARCH X-GM-RAW \"%s\"" gmail)
                                                       "SEARCH UNSEEN")))]
        (when resp
          (drop 2 (-> resp first first (clojure.string/split #" "))))))

(defn fetch-headers [sess uids]
  (let [gmail-ext (if (:gmail @sess) "X-GM-MSGID X-GM-THRID " "")
        resp (deref (do-command sess (format "fetch %s (body.peek[header] %sFLAGS BODYSTRUCTURE)" 
                                             (clojure.string/join "," uids) gmail-ext)))]
    (map parse/parse-fetch resp)))

(defn fetch-body-part
  "Fetch only the given part specified by {:path :content-type :charset} from the body.
  Returns a decoded string for now, since we only fetch texts."
  [sess uid {:keys [path content-type charset encoding] :as part}]
  (let [resp (deref (do-command sess (format "fetch %s body.peek[%s]" uid path)))
        data (-> (parse/parse-fetch (first resp))
                 :body
                 (get path))
        charset (or charset "utf-8") ; some bodystructures forget the charset
        text (case encoding
               "base64" (String. (codec/base64-decode (String. data)) charset)
               "quoted-printable" (h/decode-quopri (String. data) charset)
               (String. (.getBytes (String. data)) charset))] 
    text))

(defn connect
  "Connects to an IMAP server and returns a session.
  Arg can be either :gmail, :outlook, or a map with :server and :port.
  :gmail automatically uses Gmail IMAP extensions for the session."
  [{:keys [server port] :as arg}]
  (let [[server port] (case arg :gmail ["imap.gmail.com" 993]
                                :outlook ["imap-mail.outlook.com" 993]
                                [server port])
        sf (SSLSocketFactory/getDefault)
        sock (doto (.createSocket sf server port)
                   .startHandshake)
        w (BufferedWriter. (OutputStreamWriter. (.getOutputStream sock)))
        r (BufferedReader. (InputStreamReader. (.getInputStream sock) "US-ASCII"))
        sess (ref {:w w :r r :idx 0 :last-error nil :gmail (= :gmail arg)})
        _ (.readLine r)]
    sess))

(defn login [sess login pwd]
  (let [login (format "login %s %s" login pwd)
        resp (deref (do-command sess login))]
    (when resp sess)))

(defn authenticate [sess username token]
  (let [auth (.getBytes (format "user=%s^auth=Bearer %s^^" username token))
        idx [(+ 5 (count username)) (- (count auth) 2) (- (count auth) 1)]
        _ (doall (map #(aset-byte auth % 1) idx))
        enc (String. (codec/base64-encode auth))
        cmd (format "authenticate XOAUTH2 %s" enc)
        resp (deref (do-command sess cmd))]
    (when resp sess)))

(defn logout [sess]
  (do-command sess "logout"))
