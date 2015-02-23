(ns minimap.core
  (:use [plumbing.core])
  (:require [minimap.imap :as imap]
            [minimap.message :as msg]
            [clojure.string :as string]))

(defn login
  "Returns an imap session connected to the given account.
  Session is mutable, since anyway imap is a super stateful protocol"
  [server login password]
  (let [session (imap/connect server)]
    (imap/login session login password)))

(defn authenticate
  "Returns an imap session connected to the given account.
  Session is mutable, since anyway imap is a super stateful protocol"
  [server username token]
  (let [session (imap/connect server)]
    (imap/authenticate username token)))

(defn search
  "Returns a list of messages with their headers and basic information,
  but no body data.
  query is a map that can contain the following keys:
  :gmail    a X-GM-RAW Gmail search query
  :max      the maximum number of results (note that IMAP does not support this,
            it's just a filter we apply after initial UIDs search)"
            
  [session {:keys [max] :as query}]
  (->> (imap/search session query)
       reverse ; IMAP (Gmail at least) sends older messages first
       (?>> max take max)
       (imap/fetch-headers session)
       (map msg/assoc-common)))

(defn fetch
  "Fetches the first text/plain and/or text/html parts of the body of the msg.
  The provided msg map must contain at least :uid and :bodystructure
  parts can be :plain, :html, :plain-or-html (fetches html if no plain)"
  [session parts msg]
  (let [[plain-path html-path] (msg/text-parts msg)]
    (let [msg (if (and plain-path (#{:plain :plain-or-html} parts))
                (let [m (imap/fetch-body-part session (:uid msg) plain-path)]
                  (assoc msg :plain (string/replace m #"\r\n" "\n")))
                msg)
          msg (if (and html-path (or (= :html parts) 
                                     (and (= :plain-or-html parts) (nil? (:plain msg)))))
                (let [m (imap/fetch-body-part session (:uid msg) html-path)]
                  (assoc msg :html m))
                msg)]
      msg)))

(defn logout
  [session]
  (imap/logout session))

;; quick test function

(defn go [pwd]
  (let [session (login :gmail "a@lxbrun.com" pwd)
        msgs (->> (search session {:gmail "after:2014/9/1" :max 10})
                  (map #(fetch session :plain-or-html %)))
        subjects (map :from msgs)]
    #_(logout session)
    subjects))
