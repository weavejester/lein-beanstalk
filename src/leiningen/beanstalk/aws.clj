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
    com.amazonaws.services.elasticbeanstalk.model.ConfigurationOptionSetting
    com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest
    com.amazonaws.services.elasticbeanstalk.model.CreateEnvironmentRequest
    com.amazonaws.services.elasticbeanstalk.model.DeleteApplicationRequest
    com.amazonaws.services.elasticbeanstalk.model.DeleteApplicationVersionRequest
    com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest
    com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentRequest
    com.amazonaws.services.elasticbeanstalk.model.S3Location
    com.amazonaws.services.elasticbeanstalk.model.TerminateEnvironmentRequest
    com.amazonaws.services.elasticbeanstalk.model.EnvironmentTier
    com.amazonaws.services.s3.AmazonS3Client
    com.amazonaws.services.s3.model.Region))

(defn quiet-logger
  "Stop the extremely verbose AWS logger from logging so many messages."
  []
  (. (Logger/getLogger "com.amazonaws")
     (setLevel Level/WARNING)))

(def ^{:private true} credentials-example
  "(def lein-beanstalk-credentials {:access-key \"XXX\" :secret-key \"YYY\"})")

(defn- find-credentials
  [project]
  (let [init-map (resolve 'user/lein-beanstalk-credentials)
        creds (and init-map @init-map)]
    ((juxt :access-key :secret-key) (or creds (:aws project)))))

(defn credentials [project]
  (let [[access-key secret-key] (find-credentials project)]
    (if access-key
      (BasicAWSCredentials. access-key secret-key)
      (throw (IllegalStateException.
              (str "No credentials found; please add to ~/.lein/init.clj: "
                   credentials-example))))))

(defonce current-timestamp
  (.format (SimpleDateFormat. "yyyyMMddHHmmss") (Date.)))

(defn app-name [project]
  (or (-> project :aws :beanstalk :app-name)
      (:name project)))

(defn app-tier [project]
  (doto (EnvironmentTier.)
    (.setName (or (-> project :aws :beanstalk :app-tier :name)
                  "WebServer"))
    (.setType (or (-> project :aws :beanstalk :app-tier :type)
                  "Standard"))
    (.setVersion (or (-> project :aws :beanstalk :app-tier :version)
                     "1.0"))))

(defn app-version [project]
  (if (nil? (:version project))
    (str current-timestamp)
    (str (:version project) "-" current-timestamp)))


(defn s3-bucket-name [project]
  (or (-> project :aws :beanstalk :s3-bucket)
      (str "lein-beanstalk." (app-name project))))

(defn- ep [endpoint region_name]
  {:ep endpoint :region (Region/valueOf region_name)})

(def s3-endpoints
  {:us-east-1      (ep "s3.amazonaws.com" "US_Standard")
   :us-west-1      (ep "s3-us-west-1.amazonaws.com" "US_West")
   :us-west-2      (ep "s3-us-west-2.amazonaws.com" "US_West_2")
   :eu-west-1      (ep "s3-eu-west-1.amazonaws.com" "EU_Ireland")
   :ap-southeast-1 (ep "s3-ap-southeast-1.amazonaws.com" "AP_Singapore")
   :ap-southeast-2 (ep "s3-ap-southeast-2.amazonaws.com" "AP_Sydney")
   :ap-northeast-1 (ep "s3-ap-northeast-1.amazonaws.com" "AP_Tokyo")
   :sa-east-1      (ep "s3-sa-east-1.amazonaws.com" "SA_SaoPaulo")})

(def beanstalk-endpoints
  {:us-east-1      "elasticbeanstalk.us-east-1.amazonaws.com"
   :us-west-1      "elasticbeanstalk.us-west-1.amazonaws.com"
   :us-west-2      "elasticbeanstalk.us-west-2.amazonaws.com"
   :eu-west-1      "elasticbeanstalk.eu-west-1.amazonaws.com"
   :ap-southeast-1 "elasticbeanstalk.ap-southeast-1.amazonaws.com"
   :ap-southeast-2 "elasticbeanstalk.ap-southeast-2.amazonaws.com"
   :ap-northeast-1 "elasticbeanstalk.ap-northeast-1.amazonaws.com"
   :sa-east-1      "elasticbeanstalk.sa-east-1.amazonaws.com"})

(defn project-endpoint [project endpoints]
  (-> project :aws :beanstalk (:region :us-east-1) keyword endpoints))

(defn create-bucket [client bucket region]
  (when-not (.doesBucketExist client bucket)
    (.createBucket client bucket region)))

(defn s3-upload-file
  ([project filepath]
    (let [bucket  (s3-bucket-name project)
          file    (io/file filepath)
          ep-desc (project-endpoint project s3-endpoints)]
      (doto (AmazonS3Client. (credentials project))
        (.setEndpoint (:ep ep-desc))
        (create-bucket bucket (:region ep-desc))
        (.putObject bucket (.getName file) file))
      (println "Uploaded" (.getName file) "to S3 Bucket")))
  ([project filepath filename]
    (let [bucket  (s3-bucket-name project)
          file    (io/file filepath)
          ep-desc (project-endpoint project s3-endpoints)]
      (doto (AmazonS3Client. (credentials project))
        (.setEndpoint (:ep ep-desc))
        (create-bucket bucket (:region ep-desc))
        (.putObject bucket filename file))
      (println "Uploaded" filename "to S3 Bucket"))))

(defn- beanstalk-client [project]
  (doto (AWSElasticBeanstalkClient. (credentials project))
    (.setEndpoint (project-endpoint project beanstalk-endpoints))))

(defn create-app-version
  [project filename]
  (.createApplicationVersion
    (beanstalk-client project)
    (doto (CreateApplicationVersionRequest.)
      (.setAutoCreateApplication true)
      (.setApplicationName (app-name project))
      (.setVersionLabel (app-version project))
      (.setDescription (:description project))
      (.setSourceBundle (S3Location. (s3-bucket-name project) filename))))
  (println "Created new app version" (app-version project)))

(defn delete-app-version
  [project version]
  (.deleteApplicationVersion
    (beanstalk-client project)
    (doto (DeleteApplicationVersionRequest.)
      (.setApplicationName (app-name project))
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
       (find-one #(= (.getApplicationName %) (app-name project)))))

(defn default-env-vars
  "A map of default environment variables."
  [project]
  (let [[access-key secret-key] (find-credentials project)]
    {"AWS_ACCESS_KEY_ID" access-key
     "AWS_SECRET_KEY" secret-key}))

(defn env-var-options [project options]
  (for [[key value] (merge (default-env-vars project)
                           (-> project :aws :beanstalk :env)
                           (:env options))]
    (ConfigurationOptionSetting.
     "aws:elasticbeanstalk:application:environment"
     (if (keyword? key)
       (-> key name str/upper-case (str/replace "-" "_"))
       key)
     value)))

(defn extra-options
  [options]
  (apply concat
         (for [[namespace keyvals] options]
           (for [[key value] keyvals]
             (ConfigurationOptionSetting. namespace key value)))))

(defn create-environment [project env]
  (println (str "Creating '" (:name env) "' environment")
           "(this may take several minutes)")
  (.createEnvironment
    (beanstalk-client project)
    (let [request (CreateEnvironmentRequest.)]
      (doto request
        (.setApplicationName (app-name project))
        (.setEnvironmentName (:name env))
        (.setTier (app-tier project))
        (.setVersionLabel   (app-version project))
        (.setOptionSettings (concat (env-var-options project env)
                                    (extra-options (merge (-> project :aws :beanstalk :options)
                                                          (:options env)))))
        (.setSolutionStackName (or (-> project :aws :beanstalk :stack-name)
                                   "32bit Amazon Linux running Tomcat 7")))
      (if (= (.getName (.getTier request)) "WebServer")
        (.setCNAMEPrefix request (:cname-prefix env)))
      request)))

(defn update-environment-settings [project env options]
  (.updateEnvironment
    (beanstalk-client project)
    (doto (UpdateEnvironmentRequest.)
      (.setEnvironmentId   (.getEnvironmentId env))
      (.setEnvironmentName (.getEnvironmentName env))
      (.setOptionSettings (env-var-options project options)))))

(defn update-environment-version [project env]
  (.updateEnvironment
    (beanstalk-client project)
    (doto (UpdateEnvironmentRequest.)
      (.setEnvironmentId   (.getEnvironmentId env))
      (.setEnvironmentName (.getEnvironmentName env))
      (.setVersionLabel   (app-version project)))))

(defn app-environments [project]
  (->> (beanstalk-client project)
      .describeEnvironments
      .getEnvironments
      (filter #(= (.getApplicationName %) (app-name project)))))

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

(defn update-environment [project env {name :name :as options}]
  (println (str "Updating '" name "' environment")
           "(this may take several minutes)")
  (update-environment-settings project env options)
  (poll-until ready? #(get-env project name))
  (update-environment-version project env))

(defn deploy-environment
  [project {name :name :as options}]
  (if-let [env (get-running-env project name)]
    (update-environment project env options)
    (create-environment project options))
  (let [env (poll-until ready? #(get-env project name))]
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
