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
    com.amazonaws.services.elasticbeanstalk.model.CreateEnvironmentRequest
    com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentRequest
    com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest
    com.amazonaws.services.elasticbeanstalk.model.S3Location
    com.amazonaws.services.s3.AmazonS3Client))

(defn credentials [project]
  (BasicAWSCredentials.
    (-> project :aws :access-key)
    (-> project :aws :secret-key)))

(defn beanstalk-client [project]
  (AWSElasticBeanstalkClient. (credentials project)))

(defn s3-name [project]
  (str (:name project) "-" (:version project)))

(defn create-version [project]
  (println "Creating new application version")
  (.createApplicationVersion
    (beanstalk-client project)
    (doto (CreateApplicationVersionRequest.)
      (.setAutoCreateApplication true)
      (.setApplicationName (:name project))
      (.setDescription (:description project))
      (.setVersionLabel (:version project))
      (.setSourceBundle (S3Location. "lein-beanstalk" (s3-name project))))))

(defn upload-war [project war-file]
  (println "Uploading war file to S3")
  (doto (AmazonS3Client. (credentials project))
    (.createBucket "lein-beanstalk")
    (.putObject "lein-beanstalk" (s3-name project) (file war-file))))

(defn project-env-exists? [project environment]
  (let [envs (-> project :aws :beanstalk :environments)]
    (contains? (set (map str envs)) environment)))

(defn deploy-to-environment [project environment]
  (.createEnvironment
    (beanstalk-client project)
    (doto (CreateEnvironmentRequest.)
      (.setApplicationName (:name project))
      (.setEnvironmentName environment)
      (.setVersionLabel (:version project))
      (.setSolutionStackName "32bit Amazon Linux running Tomcat 6"))))

(defn deploy
  "Deploy the current project to Amazon Elastic Beanstalk."
  ([project]
     (println "Usage: lein beanstalk deploy <environment>"))
  ([project environment]
     (if-not (project-env-exists? project environment)
       (println (str "Environment '" environment "' not in project.clj"))
       (let [version  (:version project)
             war-file (str (:name project) "-" version "-beanstalk.war")]
         (println "Generating war file")
         (uberwar project war-file)
         (upload-war project war-file)
         (create-version project)
         (deploy-to-environment project environment)))))

(defn beanstalk
  "Manage Amazon's Elastic Beanstalk service."
  {:help-arglists '([deploy])
   :subtasks [#'deploy]}
  ([project]
     (println (help-for "beanstalk")))
  ([project subtask & args]
    (case subtask
      "deploy" (apply deploy project args))))
