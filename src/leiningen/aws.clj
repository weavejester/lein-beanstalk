(ns leiningen.aws

  (:use [clojure.java.io :only (file)]
        [clojure.string :only (lower-case)]
        [clj-time.core :only (now)]
        [clj-time.format :only (formatter unparse)])

  (:import com.amazonaws.auth.BasicAWSCredentials
           com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient
           com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest
           com.amazonaws.services.elasticbeanstalk.model.CreateEnvironmentRequest
           com.amazonaws.services.elasticbeanstalk.model.DeleteApplicationRequest
           com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest
           com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentRequest
           com.amazonaws.services.elasticbeanstalk.model.S3Location
           com.amazonaws.services.s3.AmazonS3Client))

;;
;; Amazon Web Services functions
;;
(defn- credentials
  "Returns the AWS credentials."
  [project]
  (BasicAWSCredentials.
   (get-in project [:aws :access-key])
   (get-in project [:aws :secret-key])))

;;
;; Helper functions
;;
(defonce current-datetime (unparse (formatter "yyyyMMddHHmmss") (now)))

(defn create-version
  "Creates version by appending date & time to project version."
  [project]
  (str (:version project) "-" current-datetime))

(defn versioned-filename
  "Creates version by appending date & time before the suffix."
  [project]
  (str (:name project) "-" (:version project) "-" current-datetime ".war"))

(defn- default-bucket-name
  "Returns default bucket name for the project."
  [project]
  (if-let [bucket-name (get-in project [:aws :s3location])]
    bucket-name
    (lower-case (str (:name project) "-" (subs (.getAWSAccessKeyId (credentials project)) 0 9)))))

;;
;; Amazon S3 functions
;;
(defn- s3-client
  [project]
  (AmazonS3Client. (credentials project)))

(defn- create-bucket
  "Create S3 bucket. Returns newly created bucket
   or nil when it already exists.

   Note: The bucket namespace is global, i.e. the bucket might
   exists, but was created by another user."
  [project bucket-name]
  (let [client (s3-client project)]
    (when-not (.doesBucketExist client bucket-name)
      (.createBucket client bucket-name))))

(defn upload-file-to-bucket
  "Uploads the file to the bucket. Returns the
   uploaded filenname/S3 key.

   Bucket is created if it doesnot exists."
  ([project filename]
     (upload-file-to-bucket project filename (default-bucket-name project)))
  ([project filename bucket-name]
     (let [client (s3-client project)
           bucket (create-bucket project bucket-name)
           obj (file filename)]
       (.putObject client bucket-name filename obj))))

;;
;; Amazon Elastic Beanstalk functions
;;
(defn- beanstalk-client
  [project]
  (AWSElasticBeanstalkClient. (credentials project)))

;; Application
(defn- create-application-request
  [project]
  (doto (CreateApplicationVersionRequest.)
    (.setApplicationName (:name project))
    (.setDescription (:description project))))

(defn create-application
  [project filename]
  (.createApplication (beanstalk-client project) (create-application-request project)))

(defn- create-application-version-request
  ([project filename]
     (create-application-version-request project filename (default-bucket-name project)))
  ([project filename bucket-name]
     (doto (CreateApplicationVersionRequest.)
       (.setAutoCreateApplication false)
       (.setApplicationName (:name project))
       (.setVersionLabel (create-version project))
       (.setDescription (:description project))
       (.setSourceBundle (S3Location. bucket-name filename)))))

(defn create-application-version
  [project filename]
  (.createApplicationVersion (beanstalk-client project) (create-application-version-request project filename)))

(defn describe-applications
  [project]
  (.getApplications (.describeApplications (beanstalk-client project))))

;; Environment
(defn- create-environment-request
  ([project environment]
     (create-environment-request project environment "32bit Amazon Linux running Tomcat 6"))
  ([project environment solution-stack-name]
     (doto (CreateEnvironmentRequest.)
       (.setApplicationName (:name project))
       (.setEnvironmentName environment)
       (.setVersionLabel (:version project))
       (.setSolutionStackName solution-stack-name))))

(defn- create-environment
  [project environment]
  (.createEnvironment (beanstalk-client project) (create-environment-request project environment)))

(defn- create-update-environment-request
  [project environment]
  (doto (UpdateEnvironmentRequest.)
    (.setEnvironmentId (.getEnvironmentId environment))
    (.setEnvironmentName (.getEnvironmentName environment))
    (.setVersionLabel (create-version project))))

(defn describe-environments
  [project]
  (.getEnvironments (.describeEnvironments (beanstalk-client project))))

(defn get-environment
  "Returns the environment matching the passed in name"
  [project environment-name]
  (when-let [env (filter #(and (= (.getApplicationName %) (:name project))
                               (= (.getEnvironmentName %) environment-name))
                         (describe-environments project))]
    (first env)))

(defn update-environment
  [project environment-name]
  (when-let [env (get-environment project environment-name)]
    (.updateEnvironment (beanstalk-client project) (create-update-environment-request project env))))
