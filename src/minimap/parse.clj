(ns minimap.parse
  (:require [minimap.headers :as h]
    				[clojure.string :as str]))

(defn read-from [[token & more] acc]
  (case token
    ")" [more acc]
    "(" (let [[new-tokens new-acc] (read-from more [])]
          (read-from new-tokens (conj acc new-acc)))
    nil [nil acc]
    (read-from more (conj acc token))))

(defn simple-bodystructure [[text plain charset _ _ encoding]]
  (let [clean #(-> % str/lower-case (str/replace "\"" ""))
        charset-name (if (vector? charset)
                       (-> charset second clean)
                       nil)] ; sometime we have NIL instead of ["CHARSET" "UTF-8"]
	  {:content-type (format "%s/%s" (clean text) (clean plain))
	   :charset charset-name
	   :encoding (clean encoding)}))

(defn walk-bodystructure [[head & tail :as tree] path]
  (cond
    (vector? head)
    (mapcat
      (fn [idx] (walk-bodystructure (nth tree idx) (conj path idx)))
      (range (count tree)))
    
    (= "\"TEXT\"" head) ; not interested in others
    [(merge {:path (reduce #(str %1 "." %2) (map (comp str inc) (if (empty? path) [0] path)))} (simple-bodystructure tree))]
    
    :else ; head is nil
    []))
    
(defn walk-fetch [[field content & more] msg]
  (cond
    (nil? field) 							[nil msg]
    (= "BODYSTRUCTURE" field) (recur more (assoc msg :bodystructure (walk-bodystructure content [])))
    (= "X-GM-THRID" field) 		(recur more (assoc msg :thread-id content))
    (= "X-GM-MSGID" field)    (recur more (assoc msg :msg-id content))
    (= "BODY[HEADER]" field) 	(recur (next more) (assoc msg :headers (h/parse (apply str (first more)))))
    (= "FLAGS" field)         (recur more (assoc msg :flags content))
    (re-find #"BODY\[([\d\.]+)\]" field)
    													(let [[_ path] (re-find #"BODY\[([\d\.]+)\]" field)]
      													(recur (next more) (assoc-in msg [:body path] (first more))))
    :else 										(prn "Unknown field " field content more)))

(defn walk [[star uid fetch & more :as tree]]
  (assert (and (= "*" star)
         			 (= "FETCH" fetch)
               (re-find #"^\d+$" uid)) (format "Cannot walk weird tree %s" tree))
  (second (walk-fetch (first more) {:uid uid})))
    
(declare regex) ; at the end of the file since it breaks my Clojure colozization...
    
; "* 52406 FETCH (X-GM-THRID 1476168181745142452 BODYSTRUCTURE ((\"TEXT\" \"PLAIN\" (\"CHARSET\" \"UTF-8\") NIL NIL \"7BIT\" 1022 22 NIL NIL NIL)(\"TEXT\" \"HTML\" (\"CHARSET\" \"UTF-8\") NIL NIL \"7BIT\" 1546 21 NIL NIL NIL) \"ALTERNATIVE\" (\"BOUNDARY\" \"--==_mimepart_53ebaa9a136e5_5a1c3fa51f0012bc64923\" \"CHARSET\" \"UTF-8\") NIL NIL) BODY[HEADER] {3383}"
;
(defn parse-fetch [resp] ; resp is typically a vec ["* UID FETCH (... {124}" char-array ")"]
  (let [tokens (mapcat #(if (string? %)
                          (re-seq regex %)
                          [%])
                       resp)
        [ts tree] (read-from tokens [])]
    ;(prn tree)
		(walk tree)))
                                  
(def regex #"\*|\(|\)|[A-Za-z-\[\]\d\\\.]+|\"[^\"]*\"|\{\d+\}") 

