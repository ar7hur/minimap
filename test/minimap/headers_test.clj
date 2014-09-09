(ns minimap.headers-test
  (:use clojure.test
        minimap.headers)
  (:require [clj-time.core :as t]))

(deftest encoding-tests
  (testing "base64 and Q encoded subject"
    (is (= "Account Alert: Your Account Snapshot"
           (decode-rfc2047 "=?UTF-8?B?QWNjb3VudCBBbGVydDogWW91ciBBY2NvdW50IFNuYXBzaG90?=")))
    (is (= "éléonore"
           (decode-rfc2047 "=?UTF-8?B?w6lsw6lvbm9yZQ==?=")))
    (is (= "你好"
           (decode-rfc2047 "=?UTF-8?B?5L2g5aW9?=")))
    (is (= "\"Julie_Lebrun\" <a@lxbrun.com>"
           (decode-rfc2047 "\"=?UTF-8?Q?Julie_Lebrun?=\" <a@lxbrun.com>"))))
  (testing "non encoded subject"
    (is (= "Hello"
           (decode-rfc2047 "Hello"))))
  (testing "quoted printable"
    (is (= "If you believe that truth=beauty, then surely mathematics is the most beautiful branch of philosophy."
           (decode-quopri "If you believe that truth=3Dbeauty, then surely =\r\nmathematics is the most beautiful branch of philosophy." "UTF-8")))))

(def h "Subject: =?UTF-8?B?w6lsw6lvbm9yZSBlc3QgZnNtZiBqc2tsZm0gc2pma2wgZmprbGYgamtqw6lraiBqw6nDqQ==?=\r
       =?UTF-8?B?w6lqayBsaiBtamQga2ZtanNmIHNkbG1maiBxc21mbGogZmxqbSBsw6lqa8OpbCBqw6lrbMOpasOpa2wg?=\r\n")

(deftest headers-test
  (testing "unfold"
    (is (= '("name:value")
           (unfold "name:value\r\n")))
    (is (= '("name:long value")
           (unfold "name:long\r\n value\r\n")))
    (is (= '("name:long value", "foo:bar")
   	       (unfold "name:long\r\n value\r\nfoo:bar"))))
  (testing "namevalue"
    (is (= ["foo" "bar"]
           (namevalue "foo: bar"))))
  (testing "date"
    (is (= (t/date-time 2014 8 17 13 38 15)
           (decode-date "Sun, 17 Aug 2014 06:38:15 -0700")))
    (is (= (t/date-time 2014 6 30 21 05 56)
           (decode-date "Mon, 30 Jun 2014 21:05:56 +0000 (GMT+00:00)"))) ; putain Apple!
    (is (= (t/date-time 2014 8 15 10 50 33) 
           (decode-date "Fri, 15 Aug 2014 10:50:33 +0000 (UTC)")))) ; LinkedIn emails error!
  (testing "parse"
    (is (= (list ["Subject" "éléonore est fsmf jsklfm sjfkl fjklf jkjékj jéé éjk lj mjd kfmjsf sdlmfj qsmflj fljm léjkél jékléjékl "])
           (parse h)))))
