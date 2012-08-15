(defproject lein-beanstalk "0.2.4-SNAPSHOT"
  :description "Leiningen plugin for Amazon's Elastic Beanstalk"
  :url "https://github.com/weavejester/lein-beanstalk"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [com.amazonaws/aws-java-sdk "1.3.14"]
                 [lein-ring "0.7.2"]]
  :eval-in-leiningen true)
