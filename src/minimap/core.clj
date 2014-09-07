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
  Right now query is a map that can contain a :gmail key with a gmail search
  query that you would type in the web app, like 'label:Her'"
  [session query]
  (->> (imap/search session query)
       (imap/fetch-headers session)
       (map msg/assoc-common)))

(defn fetch
  "Fetches the first text/plain and text/html parts of the body of the msg.
  The provided msg map must contain at least :uid and :bodystructure"
  [session msg]
  (prn "fetching" msg)
  (let [[plain-path html-path] (msg/text-parts msg)]
    (prn plain-path html-path)
    (let [msg (if plain-path
                (let [m (imap/fetch-body-part session (:uid msg) plain-path)]
                  (assoc msg :plain (string/replace m #"\r\n" "\n")))
                msg)
          msg (if html-path
                (let [m (imap/fetch-body-part session (:uid msg) html-path)]
                  (assoc msg :html m))
                msg)]
      msg)))

(defn logout
  [session]
  (imap/logout session))

(defn go []
  (let [session (login :gmail "a@lxbrun.com" "secret")
        msgs (search session {})]

    (prn (fetch session (first msgs)))
    (logout session)))
