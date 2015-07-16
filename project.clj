(defproject lein-beanstalk "0.2.7"
  :description "Leiningen plugin for Amazon's Elastic Beanstalk"
  :url "https://github.com/weavejester/lein-beanstalk"
  :dependencies [[com.amazonaws/aws-java-sdk "1.10.5.1"]

                 ;; The dependencies prevent the runtime error:
                 ;; NoSuchMethodError JsonFactory.requiresPropertyOrdering()Z
                 [com.fasterxml.jackson.core/jackson-core "2.2.3"]
                 [com.fasterxml.jackson.core/jackson-databind "2.2.3"]

                 [lein-ring "0.9.6"]]
  :eval-in-leiningen true)
