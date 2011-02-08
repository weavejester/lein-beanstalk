(defproject lein-beanstalk "0.1.0"

  :description "Leiningen plugin for Amazon's Elastic Beanstalk"

  :url "https://github.com/weavejester/lein-beanstalk"

  :dependencies [[org.clojure/clojure "1.2.0"]
                 [com.amazonaws/aws-java-sdk "1.1.5"]
                 [lein-ring "0.3.2"]
                 [clj-time "0.3.0-SNAPSHOT"]]

  :dev-dependencies [[swank-clojure "1.2.1"]
                     [jline "0.9.94"]
                     [leiningen "1.4.2"]])
