(ns leiningen.beanstalk.aws
  "AWS-specific libraries."
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    java.text.SimpleDateFormat
    java.util.Date
    [java.util.logging Logger Level]
    com.amazonaws.auth.BasicAWSCredentials
    com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient
    com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest
    com.amazonaws.services.elasticbeanstalk.model.CreateEnvironmentRequest
    com.amazonaws.services.elasticbeanstalk.model.DeleteApplicationRequest
    com.amazonaws.services.elasticbeanstalk.model.DeleteApplicationVersionRequest
    com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest
    com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentRequest
    com.amazonaws.services.elasticbeanstalk.model.S3Location
    com.amazonaws.services.elasticbeanstalk.model.TerminateEnvironmentRequest
    com.amazonaws.services.s3.AmazonS3Client))

(defn quiet-logger
  "Stop the extremely verbose AWS logger from logging so many messages."
  []
  (. (Logger/getLogger "com.amazonaws")
     (setLevel Level/WARNING)))

(defn credentials [project]
  (BasicAWSCredentials.
    (get-in project [:aws :access-key])
    (get-in project [:aws :secret-key])))

(defonce current-timestamp
  (.format (SimpleDateFormat. "yyyyMMddHHmmss") (Date.)))

(defn app-version [project]
  (str (:version project) "-" current-timestamp))

(defn s3-bucket-name [project]
  (or (-> project :aws :beanstalk :s3-bucket)
      (str "lein-beanstalk." (:name project))))

(defn s3-upload-file [project filepath]
  (let [bucket (s3-bucket-name project)
        file   (io/file filepath)]
    (doto (AmazonS3Client. (credentials project))
      (.createBucket bucket)
      (.putObject bucket (.getName file) file))
    (println "Uploaded" (.getName file) "to S3 Bucket")))

(defn- beanstalk-client [project]
  (AWSElasticBeanstalkClient. (credentials project)))

(defn create-app-version
  [project filename]
  (.createApplicationVersion
    (beanstalk-client project)
    (doto (CreateApplicationVersionRequest.)
      (.setAutoCreateApplication true)
      (.setApplicationName (:name project))
      (.setVersionLabel (app-version project))
      (.setDescription (:description project))
      (.setSourceBundle (S3Location. (s3-bucket-name project) filename))))
  (println "Created new app version" (app-version project)))

(defn delete-app-version
  [project version]
  (.deleteApplicationVersion
    (beanstalk-client project)
    (doto (DeleteApplicationVersionRequest.)
      (.setApplicationName (:name project))
      (.setVersionLabel version)
      (.setDeleteSourceBundle true))))

(defn get-application
  "Returns the application matching the passed in name"
  [project]
  (->> (beanstalk-client project)
       .describeApplications
       .getApplications
       (filter #(= (.getApplicationName %) (:name project)))
       first))

(defn create-environment [project env]
  (.createEnvironment
    (beanstalk-client project)
    (doto (CreateEnvironmentRequest.)
      (.setApplicationName (:name project))
      (.setEnvironmentName (:name env))
      (.setVersionLabel (app-version project))
      (.setCNAMEPrefix (:cname-prefix env))
      (.setSolutionStackName "32bit Amazon Linux running Tomcat 6")))
  (println (str "Creating '" (:name env) "' environment")
           "(this may take several minutes)"))

(defn update-environment [project env]
  (.updateEnvironment
    (beanstalk-client project)
    (doto (UpdateEnvironmentRequest.)
      (.setEnvironmentId (.getEnvironmentId env))
      (.setEnvironmentName (.getEnvironmentName env))
      (.setVersionLabel (app-version project))))
  (println (str "Updating '" (.getEnvironmentName env) "' environment")
           "(this may take several minutes)"))

(defn app-environments [project]
  (->> (beanstalk-client project)
      .describeEnvironments
      .getEnvironments
      (filter #(= (.getApplicationName %) (:name project)))))

(defn get-environment [project environment]
  (->> (app-environments project)
       (filter #(and (= (.getEnvironmentName %) environment)
                     (= (.getStatus %) "Ready")))
       first))

(defn get-env-when-ready [project env-name]
  (loop []
    (Thread/sleep 2000)
    (print ".")
    (.flush *out*)
    (or (get-environment project env-name)
        (recur))))

(defn deploy-environment
  [project options]
  (if-let [env (get-environment project (:name options))]
    (update-environment project env)
    (create-environment project options))
  (let [env (get-env-when-ready project (:name options))]
    (println " Done")
    (println "Environment deployed at:" (.getCNAME env))))

(defn terminate-environment
  [project env-name]
  (when-let [env (get-environment project env-name)]
    (.terminateEnvironment
     (beanstalk-client project)
     (doto (TerminateEnvironmentRequest.)
       (.setEnvironmentId (.getEnvironmentId env))
       (.setEnvironmentName (.getEnvironmentName env))))
    (println (str "Terminated '" env-name "' environment"))))
