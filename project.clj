(defproject lein-beanstalk "0.2.8"
  :description "Leiningen plugin for Amazon's Elastic Beanstalk"
  :url "https://github.com/weavejester/lein-beanstalk"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [com.amazonaws/aws-java-sdk "1.3.31"]
                 [lein-ring "0.8.2"]]
  :eval-in-leiningen true)
