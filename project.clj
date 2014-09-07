(defproject minimap "0.1.0-SNAPSHOT"
  :main minimap.core
  :description "A minimal and partial native IMAP client with no dependencies on javax.mail"
  :url "https://github.com/ar7hur/minimap"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.codec "0.1.0"] ; FIXME switch to ring codec
                 [cheshire "5.3.1"]
                 [midje "1.6.3"]
                 [clj-time "0.8.0"]
                 [instaparse "1.3.3"]
                 [prismatic/plumbing "0.1.0"]
                 [ring/ring-codec "1.0.0"]])
