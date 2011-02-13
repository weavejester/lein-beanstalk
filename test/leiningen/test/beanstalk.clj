(ns leiningen.test.beanstalk
  (:use [leiningen.beanstalk] :reload)
  (:use [clojure.test]))

(def no-envs-project
  {:name "noenvsproject"
   :aws {:access-key "XXXXXXX" :secret-key "YYYYYYYYYYYYYY"}})

(def empty-envs-project
  {:name "emptyenvsproject"
   :aws {:access-key "XXXXXXX" :secret-key "YYYYYYYYYYYYYY"
         :beanstalk {:environments []}}})

(def vector-envs-project
  {:name "vectorenvsproject"
   :aws {:access-key "XXXXXXX" :secret-key "YYYYYYYYYYYYYY"
         :beanstalk {:environments ["dev" "demo" "prod"]}}})

(def map-envs-project-without-cname-prefix
  {:name "mapenvsproject"
   :aws {:access-key "XXXXXXX" :secret-key "YYYYYYYYYYYYYY"
         :beanstalk {:environments [{:name "dev"} {:name "demo"} {:name "prod"}]}}})

(def map-and-vector-envs-project-without-cname-prefix
  {:name "mapandvectorenvsproject"
   :aws {:access-key "XXXXXXX" :secret-key "YYYYYYYYYYYYYY"
         :beanstalk {:environments [{:name "dev"} "demo" {:name "prod"}]}}})

(def map-envs-project-with-cname-prefix
  {:name "mapenvsproject"
   :aws {:access-key "XXXXXXX" :secret-key "YYYYYYYYYYYYYY"
         :beanstalk {:environments [{:name "dev"  :cname-prefix "mapenvsproject-development"}
                                    {:name "demo" :cname-prefix "mapenvsproject-staging"}
                                    {:name "prod" :cname-prefix "mapenvsproject"}]}}})

(def map-and-vector-envs-project-with-cname-prefix
  {:name "mapandvectorenvsproject"
   :aws {:access-key "XXXXXXX" :secret-key "YYYYYYYYYYYYYY"
         :beanstalk {:environments [{:name "dev"}
                                    "demo"
                                    {:name "prod" :cname-prefix "mapandvectorenvsproject"}]}}})

(deftest test-update-project-with-default-environments
  (are [project name cname-prefix]
       (.contains (get-in (update-project project) [:aws :beanstalk :environments]) {:name name :cname-prefix cname-prefix})
       no-envs-project "development" "noenvsproject-development"
       no-envs-project "staging"     "noenvsproject-staging"
       no-envs-project "production"  "noenvsproject"
       empty-envs-project "development" "emptyenvsproject-development"
       empty-envs-project "staging"     "emptyenvsproject-staging"
       empty-envs-project "production"  "emptyenvsproject"))

(deftest test-update-project-with-vector
  (are [project name cname-prefix]
       (.contains (get-in (update-project project) [:aws :beanstalk :environments]) {:name name :cname-prefix cname-prefix})
       vector-envs-project "dev"  "vectorenvsproject-dev"
       vector-envs-project "demo" "vectorenvsproject-demo"
       vector-envs-project "prod" "vectorenvsproject-prod"))

(deftest test-update-project-with-map-without-cname-refix
  (are [project name cname-prefix]
       (.contains (get-in (update-project project) [:aws :beanstalk :environments]) {:name name :cname-prefix cname-prefix})
       map-envs-project-without-cname-prefix "dev"  "mapenvsproject-dev"
       map-envs-project-without-cname-prefix "demo" "mapenvsproject-demo"
       map-envs-project-without-cname-prefix "prod" "mapenvsproject-prod"))

(deftest test-update-project-with-map-and-vector-without-cname-prefix
  (are [project name cname-prefix]
       (.contains (get-in (update-project project) [:aws :beanstalk :environments]) {:name name :cname-prefix cname-prefix})
       map-and-vector-envs-project-without-cname-prefix "dev"  "mapandvectorenvsproject-dev"
       map-and-vector-envs-project-without-cname-prefix "demo" "mapandvectorenvsproject-demo"
       map-and-vector-envs-project-without-cname-prefix "prod" "mapandvectorenvsproject-prod"))

(deftest test-update-project-with-map-with-cname-refix
  (are [project name cname-prefix]
       (.contains (get-in (update-project project) [:aws :beanstalk :environments]) {:name name :cname-prefix cname-prefix})
       map-envs-project-with-cname-prefix "dev"  "mapenvsproject-development"
       map-envs-project-with-cname-prefix "demo" "mapenvsproject-staging"
       map-envs-project-with-cname-prefix "prod" "mapenvsproject"))

(deftest test-update-project-with-map-and-vector-with-cname-prefix
  (are [project name cname-prefix]
       (.contains (get-in (update-project project) [:aws :beanstalk :environments]) {:name name :cname-prefix cname-prefix})
       map-and-vector-envs-project-with-cname-prefix "dev"  "mapandvectorenvsproject-dev"
       map-and-vector-envs-project-with-cname-prefix "demo" "mapandvectorenvsproject-demo"
       map-and-vector-envs-project-with-cname-prefix "prod" "mapandvectorenvsproject"))
