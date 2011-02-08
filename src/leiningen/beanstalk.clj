(ns leiningen.beanstalk
  (:use [leiningen.help :only (help-for)]
        [leiningen.ring.war :only (war-file-path)]
        [leiningen.ring.uberwar :only (uberwar)]
        [clojure.java.io :only (file)]
        [clojure.string :only (join)]
        [leiningen.aws :as aws]))

(defn- project-env-exists? [project environment]
  (let [envs (-> project :aws :beanstalk :environments)]
    (contains? (set (map str envs)) environment)))

(defn deploy
  "Deploy the current project to Amazon Elastic Beanstalk."
  ([project]
     (println "Usage: lein beanstalk deploy <environment>"))
  ([project environment]
     (if-not (project-env-exists? project environment)
       (println (str "Environment '" environment "' not in project.clj"))
       (let [filename (versioned-filename project)]
         (uberwar project filename)
         (aws/upload-file-to-bucket project filename)
         (aws/create-application-version project filename)
         (aws/deploy-environment project environment)))))

(defn info
  "Provides info for about project on Amazon Elastic Beanstalk."
  ([project]
     (println "Usage: lein beanstalk info <environment>"))
  ([project environment-name]
     (if-not (project-env-exists? project environment-name)
       (println (str "Environment '" environment-name "' not in project.clj!"))
       (if-let [env (aws/get-environment project environment-name)]
         (do
           (println (str "Environment Id   : " (.getEnvironmentId env)))
           (println (str "Application Name : " (.getApplicationName env)))
           (println (str "Environment Name : " (.getEnvironmentName env)))
           (println (str "Description      : " (.getDescription env)))
           (println (str "URL              : " (.getCNAME env)))
           (println (str "LoadBalancer URL : " (.getEndpointURL env)))
           (println (str "Status           : " (.getStatus env)))
           (println (str "Health           : " (.getHealth env)))
           (println (str "Current Version  : " (.getVersionLabel env)))
           (println (str "Solution Stack   : " (.getSolutionStackName env)))
           (println (str "Created On       : " (.getDateCreated env)))
           (println (str "Updated On       : " (.getDateUpdated env))))
         (println (str "Environment '" environment-name "' not found on AWS Elastic Beanstalk!"))))))

(defn beanstalk
  "Manage Amazon's Elastic Beanstalk service."
  {:help-arglists '([deploy] info)
   :subtasks [#'deploy #'info]}
  ([project]
     (println (help-for "beanstalk")))
  ([project subtask & args]
    (case subtask
          "deploy" (apply deploy project args)
          "info"   (apply info project args))))
