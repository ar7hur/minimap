(ns minimap.message
  (:use [plumbing.core])
  (:require [clojure.string :as string]
            [cheshire.core :as j]
            [cheshire.generate :as g]
            [clj-time.format :as f]
            [clj-time.coerce :as coerce]
            [clojure.java.io :as io]))

;; add a custom encoder for org.joda.time.DateTime:
(g/add-encoder org.joda.time.DateTime
             (fn [d jsonGenerator]
               (.writeString jsonGenerator (f/unparse (f/formatters :rfc822) d))))

(def common-headers #{"Subject" "From" "To" "Date" "Cc"})

(defn text-parts
  [{:keys [bodystructure] :as msg}]
  {:pre [bodystructure]}
  "Returns [path-of-text/pain path-of-text/html]
  where path references a body part, e.g. 1.1"
  (let [tp (first (filter #(= "text/plain" (:content-type %)) bodystructure))
        th (first (filter #(= "text/html" (:content-type %)) bodystructure))]
    [tp th]))

(defn assoc-common [msg]
  "Assoc common headers and flags like Subject, To"
  (prn (:flags msg))
  (-> msg
      (into (->> (:headers msg)
                 (filter (comp common-headers first))
                 (map (fn [[k v]] [((comp keyword string/lower-case) k) v]))))
      (?> ((set (:flags msg)) "\\Flagged") assoc :flagged true)
      (?> ((set (:flags msg)) "\\Answered") assoc :answered true)))

(defn store [msg]
  (spit (format "messages/%s.json" (:msg-id msg))
        (j/generate-string msg {:pretty true}))
  msg)

(defn store-meta [msg-id meta]
  (spit (format "meta/%s.json" msg-id)
        (j/generate-string meta {:pretty true})))

(defn retrieve-obj [message-or-meta msg-id]
  (let [filename (format "%s/%s.json" message-or-meta msg-id)]
    (when (.exists (io/as-file filename))
      (-> (slurp filename)
          (j/parse-string true)))))

(defn update-meta [msg-id pred]
  (->> (retrieve-obj "meta" msg-id)
       (merge {:msg-id msg-id})
       pred
       (store-meta msg-id)))

(defn stored? [msg-id]
  (.exists (io/as-file (format "messages/%s.json" msg-id))))

(defn retrieve [msg-id]
  (when (stored? msg-id)
    (-> (retrieve-obj "messages" msg-id)
        (update-in [:date] #(coerce/to-date (f/parse (f/formatters :rfc822) %)))
        (assoc :meta (retrieve-obj "meta" msg-id)))))

(defn all-msg-ids []
  (->> (file-seq (io/file "messages"))
                     (map #(.getName %))
                     (map #(re-find #"(\d+)\.json" %))
                     (filter identity)
                     (map second)))

(defn random []
  (-> (all-msg-ids)
      rand-nth
      retrieve))

(defn all-meta-ids []
  (->> (file-seq (io/file "meta"))
       (map #(.getName %))
       (map #(re-find #"(\d+)\.json" %))
       (filter identity)
       (map second)))

(defn all-meta []
  (map #(into {:msg-id %} (retrieve-obj "meta" %)) (all-meta-ids)))

(defn lines [msg]
  "Returns a sequence of [start end] for each line in the plain text body, excluding the \n"
  (when-let [text (:plain msg)]
    (loop [idx (.indexOf text "\n" 0) start 0 acc []]
      (if (= -1 idx)
        (conj acc [start (count text)])
        (recur (.indexOf text "\n" (inc idx)) (inc idx) (conj acc [start idx]))))))


