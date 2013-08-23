(ns leiningen.beanstalk
  (:require [leiningen.beanstalk.aws :as aws]
            [clojure.string :as str]
            [clojure.set :as set])
  (:use [leiningen.help :only (help-for)]
        [leiningen.ring.war :only (war-file-path)]
        [leiningen.ring.uberwar :only (uberwar)]))

(defn default-environments
  [project]
  (let [project-name (:name project)]
    [{:alias "development" :name (str project-name "-dev")     :cname-prefix (str project-name "-dev")}
     {:alias "staging"     :name (str project-name "-staging") :cname-prefix (str project-name "-staging")}
     {:alias "production"  :name project-name                  :cname-prefix project-name}]))

(defn project-environments
  [project]
  (for [env (-> project :aws :beanstalk :environments)]
    (if (map? env)
      (merge {:cname-prefix (str (:name project) "-" (or (:alias env) (:name env)))}
        (merge {:name (or (:name env) (str (:name project) "-" (:alias env)))} env))
      {:name env, :cname-prefix (str (:name project) "-" env)})))

(defn get-project-env [project env-name]
  (->> (or (seq (project-environments project))
           (default-environments project))
       (filter #(or (= (:name %) env-name) (= (:alias %) env-name)))
       (first)))

(defn war-filename [project]
  (if (nil? (:name project))
    (str (clojure.string/replace project #".war" "") "-" (aws/app-version project) ".war")
    (str (:name project) "-" (aws/app-version project) ".war")))

(defn deploy
  "Deploy the current project to Amazon Elastic Beanstalk."
  ([project]
     (println "Usage: lein beanstalk deploy <environment>"))
  ([project env-name]
     (if-let [env (get-project-env project env-name)]
       (let [filename (war-filename project)
             path (uberwar project filename)]
         (aws/s3-upload-file project path)
         (aws/create-app-version project filename)
         (aws/deploy-environment project env))
       (println (str "Environment '" env-name "' not defined!"))))
  ([project env-name war-file]
     (if-let [env (get-project-env project env-name)]
       (let [filename (war-filename war-file)
             path war-file]
         (aws/s3-upload-file project path filename)
         (aws/create-app-version project filename)
         (aws/deploy-environment project env))
       (println (str "Environment '" env-name "' not defined!")))))

(defn terminate
  "Terminte the environment for the current project on Amazon Elastic Beanstalk."
  ([project]
     (println "Usage: lein beanstalk terminate <environment>"))
  ([project env-name]
     (let [name (:name (get-project-env project env-name))]
       (if-not name
         (println (str "Environment '" name "' not in project.clj"))
         (aws/terminate-environment project name)))))

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
  (if-let [env (aws/get-env project env-name)]
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
     (if-not (get-project-env project env-name)
       (println (str "Environment '" env-name "' not defined!"))
       (env-info project env-name))))

(defn clean
  "Cleans out old versions, except the ones currently deployed."
  [project]
  (let [all-versions      (set (.getVersions (aws/get-application project)))
        deployed-versions (set (map #(.getVersionLabel %)
                                    (aws/app-environments project)))]
    (doseq [version (set/difference all-versions deployed-versions)]
      (print (str "Removing '" version "'"))
      (aws/delete-app-version project version)
      (print (str " -> done!\n")))))

(defn beanstalk
  "Manage Amazon's Elastic Beanstalk service."
  {:help-arglists '([clean deploy info terminate])
   :subtasks [#'clean #'deploy #'info #'terminate]}
  ([project]
     (println (help-for "beanstalk")))
  ([project subtask & args]
     (aws/quiet-logger)
     (case subtask
       "clean"     (apply clean project args)
       "deploy"    (apply deploy project args)
       "info"      (apply info project args)
       "terminate" (apply terminate project args)
       (println (help-for "beanstalk")))))
