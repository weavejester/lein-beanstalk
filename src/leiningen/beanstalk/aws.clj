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

(def ^{:private true} credentials-example
  "(def lein-beanstalk-credentials {:aws {:access-key \"XXX\" :secret-key \"YYY\"}})")

(defn- creds [project]
  (let [init-map (resolve 'user/lein-beanstalk-credentials)
        creds (and init-map @init-map)]
    (when-not creds (println "WARNING: No AWS credentials found in ~/.lein/init.clj, falling back to project.clj."))
    (or creds (:aws project))))

(defn- find-credentials
  [project]
  ((juxt :access-key :secret-key) (creds project)))

(defn endpoint [project]
  (let [region (or (:region (creds project)) "us-east-1")]
    (str "elasticbeanstalk." region ".amazonaws.com")))

(defn credentials [project]
  (let [[username pass] (find-credentials project)
        endpoint (endpoint project)]
    (if username
      (BasicAWSCredentials. username pass)
      (throw (IllegalStateException. (str "No credentials found; please add to ~/.lein/init.clj: " credentials-example))))))

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
  (doto (AWSElasticBeanstalkClient. (credentials project))
    (.setEndpoint (endpoint project))))

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

(defn find-one [pred coll]
  (first (filter pred coll)))

(defn get-application
  "Returns the application matching the passed in name"
  [project]
  (->> (beanstalk-client project)
       .describeApplications
       .getApplications
       (find-one #(= (.getApplicationName %) (:name project)))))

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

(defn ready? [environment]
  (= (.getStatus environment) "Ready"))

(defn terminated? [environment]
  (= (.getStatus environment) "Terminated"))

(defn get-env [project env-name]
  (->> (app-environments project)
       (find-one #(= (.getEnvironmentName %) env-name))))

(defn get-running-env [project env-name]
  (->> (app-environments project)
       (remove terminated?)
       (find-one #(= (.getEnvironmentName %) env-name))))

(defn poll-until
  "Poll a function until its value matches a predicate."
  ([pred poll]
     (poll-until pred poll 3000))
  ([pred poll & [delay]]
     (loop []
       (Thread/sleep delay)
       (print ".")
       (.flush *out*)
       (let [value (poll)]
         (if (pred value) value (recur))))))

(defn deploy-environment
  [project options]
  (if-let [env (get-running-env project (:name options))]
    (update-environment project env)
    (create-environment project options))
  (let [env (poll-until ready? #(get-env project (:name options)))]
    (println " Done")
    (println "Environment deployed at:" (.getCNAME env))))

(defn terminate-environment
  [project env-name]
  (when-let [env (get-running-env project env-name)]
    (.terminateEnvironment
     (beanstalk-client project)
     (doto (TerminateEnvironmentRequest.)
       (.setEnvironmentId (.getEnvironmentId env))
       (.setEnvironmentName (.getEnvironmentName env))))
    (println (str "Terminating '" env-name "' environment")
             "(This may take several minutes)")
    (poll-until terminated? #(get-env project env-name))
    (println " Done")))
