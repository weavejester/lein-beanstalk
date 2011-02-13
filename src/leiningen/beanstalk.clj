(ns leiningen.beanstalk
  (:require [leiningen.beanstalk.aws :as aws]
            [clojure.string :as str])
  (:use [leiningen.help :only (help-for)]
        [leiningen.ring.war :only (war-file-path)]
        [leiningen.ring.uberwar :only (uberwar)]))

(defn- project-env-exists? [project env-name]
  (let [envs (-> project :aws :beanstalk :environments)]
    (some #(= (:name %) env-name) envs)))

(defn default-environments
  [project]
  (let [project-name (:name project)]
    [{:name "development" :cname-prefix (str project-name "-dev")}
     {:name "staging"     :cname-prefix (str project-name "-staging")}
     {:name "production"  :cname-prefix project-name}]))

(defn environments
  [project]
  (when-let [envs (not-empty (-> project :aws :beanstalk :environments))]
    (for [env envs]
      (if (map? env)
        (merge {:cname-prefix (str (:name project) "-" (:name env))} env)
        {:name env :cname-prefix (str (:name project) "-" env)}))))

(defn update-project
  "Returns project with filled in environments."
  [project]
  (assoc-in project
            [:aws :beanstalk :environments]
            (or (environments project)
                (default-environments project))))

(defn war-filename [project]
  (str (:name project) "-" (aws/app-version project) ".war"))

(defn deploy
  "Deploy the current project to Amazon Elastic Beanstalk."
  ([project]
     (println "Usage: lein beanstalk deploy <environment>"))
  ([project env-name]
     (if-not (project-env-exists? project env-name)
       (println (str "Environment '" env-name "' not defined!"))
       (let [filename (war-filename project)]
         (uberwar project filename)
         (aws/s3-upload-file project filename)
         (aws/create-app-version project filename)
         (aws/deploy-environment project env-name)))))

(defn terminate
  "Terminte the environment for the current project on Amazon Elastic Beanstalk."
  ([project]
     (println "Usage: lein beanstalk terminate <environment>"))
  ([project env-name]
     (if-not (project-env-exists? project env-name)
       (println (str "Environment '" env-name "' not in project.clj"))
       (aws/terminate-environment project env-name))))

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
       (println (str "Environment '" env-name "' not defined!"))
       (env-info project env-name))))

(defn beanstalk
  "Manage Amazon's Elastic Beanstalk service."
  {:help-arglists '([deploy info terminate])
   :subtasks [#'deploy #'info #'terminate]}
  ([project]
     (println (help-for "beanstalk")))
  ([project subtask & args]
     (let [updated-project (update-project project)]
       (case subtask
             "deploy"    (apply deploy updated-project args)
             "info"      (apply info updated-project args)
             "terminate" (apply terminate updated-project args)))))
