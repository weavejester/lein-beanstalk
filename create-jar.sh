#!bash -ex

REPO_NAME=knowledgetree
REPO_LOCATION="../kt-manager"

# Clear existing Maven repository cache for this project.
rm -rf ~/.m2/repository/$REPO_NAME/lein-beanstalk-kt

lein deps
lein compile
lein uberjar

# Create entry in the Maven repository. Change the version and repo
# location as necessary.
mvn deploy:deploy-file -Dfile=target/lein-beanstalk-0.2.8-standalone.jar \
                       -DartifactId=lein-beanstalk-kt \
                       -Dversion=0.2.8 \
                       -DgroupId=$REPO_NAME \
                       -Dpackaging=jar \
                       -DcreateChecksum=true \
                       -Durl=file:$REPO_LOCATION/maven_repository
