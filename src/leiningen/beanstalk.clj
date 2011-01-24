(ns leiningen.beanstalk
  (:use
    [leiningen.help :only (help-for)]
    [leiningen.ring.war :only (war-file-path)]
    [leiningen.ring.uberwar :only (uberwar)]
    [clojure.java.io :only (file)])
  (:import
    com.amazonaws.auth.BasicAWSCredentials
    com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient
    com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest
    com.amazonaws.services.elasticbeanstalk.model.S3Location
    com.amazonaws.services.s3.AmazonS3Client))

(defn credentials [project]
  (BasicAWSCredentials.
    (-> project :aws :access-key)
    (-> project :aws :secret-key)))

(defn create-version [project]
  (println "Creating new application version")
  (.createApplicationVersion
    (AWSElasticBeanstalkClient. (credentials project))
    (doto (CreateApplicationVersionRequest.)
      (.setAutoCreateApplication true)
      (.setApplicationName (:name project))
      (.setDescription (:description project))
      (.setVersionLabel (:version project))
      (.setSourceBundle (S3Location. "lein-beanstalk" "temp")))))

(defn upload-war [project war-file]
  (println "Uploading war file to S3")
  (doto (AmazonS3Client. (credentials project))
    (.createBucket "lein-beanstalk")
    (.putObject "lein-beanstalk" "temp" (file war-file))))

(defn deploy-to-environment [project environment])

(defn deploy
  ([project]
     (println "Usage: lein beanstalk deploy <environment>"))
  ([project environment]
     (let [version  (:version project)
           war-file (str (:name project) "-" version "-beanstalk.war")]
       (println "Generating war file")
       (uberwar project war-file)
       (upload-war project war-file)
       (create-version project)
       (deploy-to-environment project environment))))

(defn beanstalk
  "Manage Amazon's Elastic Beanstalk service."
  {:help-arglists '([deploy])
   :subtasks [#'deploy]}
  ([project]
     (println (help-for "beanstalk")))
  ([project subtask & args]
    (case subtask
      "deploy" (apply deploy project args))))
