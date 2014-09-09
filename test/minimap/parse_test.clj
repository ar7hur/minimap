(ns minimap.parse-test
  (:use clojure.test
        minimap.parse))

(def stub ["* 52406 FETCH (X-GM-THRID 1476168181745142452 BODYSTRUCTURE ((\"TEXT\" \"PLAIN\" (\"CHARSET\" \"UTF-8\") NIL NIL \"7BIT\" 1022 22 NIL NIL NIL)(\"TEXT\" \"HTML\" (\"CHARSET\" \"UTF-8\") NIL NIL \"7BIT\" 1546 21 NIL NIL NIL) \"ALTERNATIVE\" (\"BOUNDARY\" \"--==_mimepart_53ebaa9a136e5_5a1c3fa51f0012bc64923\" \"CHARSET\" \"UTF-8\") NIL NIL) BODY[HEADER] {3383}"
           (char-array "Subject: hello")
           ")"])
  
(prn (parse-fetch stub))