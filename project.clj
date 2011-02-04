(defproject lein-beanstalk "0.1.0"

  :description "Leiningen plugin for Amazon's Elastic Beanstalk"

  :dependencies [[org.clojure/clojure "1.2.0"]
                 [com.amazonaws/aws-java-sdk "1.1.3"]
                 [lein-ring "0.3.0"]]

  :dev-dependencies [[swank-clojure "1.2.1"]
                     [jline "0.9.94"]])
