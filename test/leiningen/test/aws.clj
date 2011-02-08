(ns leiningen.test.aws
  (:use [leiningen.aws] :reload)
  (:use [clojure.test]))

(deftest version-tests
  ;; maintains suffix
  (is (= "war"
         (create-version "foo-1.0.0.war"))
      "Maintains the suffix of the passed in filename."))
