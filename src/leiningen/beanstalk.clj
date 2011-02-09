(ns leiningen.beanstalk
  (:require [leiningen.aws :as aws]
            [clojure.string :as str])
  (:use [leiningen.help :only (help-for)]
        [leiningen.ring.war :only (war-file-path)]
        [leiningen.ring.uberwar :only (uberwar)]))

(defn- project-env-exists? [project environment]
  (let [envs (-> project :aws :beanstalk :environments)]
    (contains? (set (map str envs)) environment)))

(defn war-filename [project]
  (str (:name project) "-" (aws/app-version project) ".war"))

(defn deploy
  "Deploy the current project to Amazon Elastic Beanstalk."
  ([project]
     (println "Usage: lein beanstalk deploy <environment>"))
  ([project environment]
     (if-not (project-env-exists? project environment)
       (println (str "Environment '" environment "' not in project.clj"))
       (let [filename (war-filename project)]
         (uberwar project filename)
         (aws/s3-upload-file project filename)
         (aws/create-app-version project filename)
         (aws/deploy-environment project environment)))))

(def app-info-indent "\n                  ")

(defn- last-versions-info [app]
  (str/join app-info-indent (take 5 (.getVersions app))))

(defn- deployed-envs-info [project]
  (str/join
    app-info-indent
    (for [env (aws/app-environments project)]
      (str (.getEnvironmentName env) " (" (.getStatus env) ")"))))

(defn app-info
  "Displays information about a Beanstalk application."
  [project]
  (if-let [app (aws/get-application project)]
    (println (str "Application Name: " (.getApplicationName app) "\n"
                  "Last 5 Versions:  " (last-versions-info app) "\n"
                  "Created On:       " (.getDateCreated app) "\n"
                  "Updated On:       " (.getDateUpdated app) "\n"
                  "Deployed Envs:    " (deployed-envs-info project)))
    (println (str "Application '" (:name project) "' "
                  "not found on AWS Elastic Beanstalk"))))

(defn env-info
  "Displays information about a Beanstalk environment."
  [project env-name]
  (if-let [env (aws/get-environment project env-name)]    
    (println (str "Environment ID:    " (.getEnvironmentId env) "\n"
                  "Application Name:  " (.getApplicationName env) "\n"
                  "Environment Name:  " (.getEnvironmentName env) "\n"
                  "Description:       " (.getDescription env) "\n"
                  "URL:               " (.getCNAME env) "\n"
                  "Load Balancer URL: " (.getEndpointURL env) "\n"
                  "Status:            " (.getStatus env) "\n"
                  "Health:            " (.getHealth env) "\n"
                  "Current Version:   " (.getVersionLabel env) "\n"
                  "Solution Stack:    " (.getSolutionStackName env) "\n"
                  "Created On:        " (.getDateCreated env) "\n"
                  "Updated On:        " (.getDateUpdated env)))
    (println (str "Environment '" env-name "' "
                  "not found on AWS Elastic Beanstalk"))))

(defn info
  "Provides info for about project on Amazon Elastic Beanstalk."
  ([project]
     (app-info project))
  ([project env-name]
     (if-not (project-env-exists? project env-name)
       (println (str "Environment '" env-name "' not in project.clj!"))
       (env-info project env-name))))

(defn beanstalk
  "Manage Amazon's Elastic Beanstalk service."
  {:help-arglists '([deploy info])
   :subtasks [#'deploy #'info]}
  ([project]
     (println (help-for "beanstalk")))
  ([project subtask & args]
    (case subtask
      "deploy" (apply deploy project args)
      "info"   (apply info project args))))
