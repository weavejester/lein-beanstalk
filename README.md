# lein-elastic-beanstalk

Leiningen plugin for Amazon's [Elastic Beanstalk][1].

This is an updated version of @weavejester's repo.

## Prerequisites

You will need an [Amazon Web Services][2] account, and know your
account key and secret key.

You will also need to be signed up for Elastic Beanstalk.

## Basic Configuration

To use lein-beanstalk, you'll need to add a few additional values to
your `project.clj` file.

First, add lein-elastic-beanstalk as a plugin:
```clojure
:plugins [[lein-elastic-beanstalk "0.2.8-SNAPSHOT"]]
```

or, if you're using a version of Leiningen prior to 1.7.0, add it to
your `:dev-dependencies`:
```clojure
:dev-dependencies [[lein-elastic-beanstalk "0.2.8-SNAPSHOT"]]
```
Then add a `lein-beanstalk-credentials` definition to your
`~/.lein/init.clj` file that contains your AWS credentials:
```clojure
(def lein-beanstalk-credentials
  {:access-key "XXXXXXXXXXXXXXXXXX"
   :secret-key "YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"})
```

Or, if you're using Leiningen 2, you can add the credentials to your
`~/.lein/profiles.clj` file:
```clojure
{:user
 {:aws {:access-key "XXXXXXXXXXXXXXXXXX"
        :secret-key "YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"}}}
```
Finally, lein-beanstalk uses lein-ring for packaging your
application, so all of lein-ring's configuration applies as well.
At a minimum, you'll need to your `project.clj` a reference to
your application's top-level handler, e.g.:

```clojure
:ring {:handler hello-world.core/handler}
```

See the documentation for [lein-ring](https://github.com/weavejester/lein-ring)
for more about the options it provides.

### Deploy

You should now be able to deploy your application to the Amazon cloud
using the following command:

    $ lein beanstalk deploy development

#### Custom WAR

If you're so inclined, you can also deploy a custom WAR by passing the file
to the deploy command.

    $ lein beanstalk deploy development target/myproject.war

### Info

To get information about the application itself run

    $ lein beanstalk info
    Application Name : myapp
    Description      : My Awesome Compojure App
    Last 5 Versions  : 0.1.0-20110209030504
                       0.1.0-20110209030031
                       0.1.0-20110209025533
                       0.1.0-20110209021110
                       0.1.0-20110209015216
    Created On       : Wed Feb 09 03:00:45 EST 2011
    Updated On       : Wed Feb 09 03:00:45 EST 2011
    Deployed Envs    : development (Ready)
                       staging (Ready)
                       production (Terminated)

and information about a particular environment execute

    $ lein beanstalk info development
    Environment Id   : e-lm32mpkr6t
    Application Name : myapp
    Environment Name : development
    Description      : Default environment for the myapp application.
    URL              : development-feihvibqb.elasticbeanstalk.com
    LoadBalancer URL : awseb-myapp-46156215.us-east-1.elb.amazonaws.com
    Status           : Ready
    Health           : Green
    Current Version  : 0.1.0-20110209030504
    Solution Stack   : 32bit Amazon Linux running Tomcat 6
    Created On       : Tue Feb 08 08:01:44 EST 2011
    Updated On       : Tue Feb 08 08:05:01 EST 2011

### Shutdown

To shutdown an existing environment use the following command

    $ lein beanstalk terminate development

This terminates the environment and all of its resources, i.e.
the Auto Scaling group, LoadBalancer, etc.

### Cleanup

To remove any unused versions from the S3 bucket run

    $ lein beanstalk clean


##  Configuration

### AWS Credentials

The [Amazon Web Services][2] account key and secret key should be
put into a `lein-beanstalk-credentials` definition in your
`~/.lein/init.clj` file:
```clojure
(def lein-beanstalk-credentials
  {:access-key "XXXXXXXXXXXXXXXXXX"
   :secret-key "YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"})
```

Keeping your credentials out of your `project.clj` file and out
of your project in general helps ensure you don't accidentally
commit your credentials to github et al.

However, If you want to deploy your application using beanstalk from
an environment like Jenkins, where you don't have control over the
user, you can export the credential to the environment from the build
script, and inside your `project.clj`, do:
```clojure
(defproject my-project "0.1.0"
    :description ...
    :aws {
        :access-key ~(System/getenv "AWS_ACCESS_KEY")
        :secret-key ~(System/getenv "AWS_SECRET_KEY")})
```
### Environments

Elastic Beanstalk environments can be defined in multiple ways in
the `project.clj` file.

If no environments are specified, lein-beanstalk will create three
default environments

* `development` (with CNAME prefix `myapp-development`)
* `staging` (with CNAME prefix `myapp-staging`)
* `production` (with CNAME prefix `myapp`)

To override the default behavior, add an `:aws` key to your
`project.clj` file, either with `:environments` mapped to a
vector of envionment symbols:
```clojure
:aws {:beanstalk {:environments [dev demo prod]
                  ...}
      ...}
```

or to a vector of maps
```clojure
:aws {:beanstalk {:environments [{:name "dev"}
                                 {:name "demo"}
                                 {:name "prod"}]
                  ...}
      ...}
```
Given either of the above configurations, the following two
environents will be created:

* `dev` (with CNAME prefix `myapp-dev`)
* `demo` (with CNAME prefix `myapp-demo`)
* `prod` (with CNAME prefix `myapp-prod`)

The second option allows one to specify the CNAME prefix for each
environment
```clojure
:aws {:beanstalk {:environments [{:name "dev"
                                  :cname-prefix "myapp-development"}
                                 {:name "staging"
                                  :cname-prefix "myapp-demo"}
                                 {:name "prod"
                                  :cname-prefix "myapp"}]
                  ...}
      ...}
```
By default the CNAME prefix is `<project-name>-<environment>`.

### Aliases

If you deploy multiple services to Elastic Beanstalk, you'll realize
that environment names must be unique across all of your applications.
Aliases allow you to refer to a standard name across your projects,
while deploying to an environment named suing either the defaults or
what is supplied for `:name`.


Below are the defaults.
```clojure
:aws {:beanstalk {:environments [{:alias "development"
                                  :name "myproject-dev"}
                                 {:alias "staging"
                                  :name "myproject-staging"}
                                 {:alias "production"
                                  :name "myproject"}]
                  ...}
      ...}
```

You may refer to either the alias or the name when running lein-beanstalk
commands.

    $ lein beanstalk deploy development
    $ lein beanstalk deploy myproject-dev

Both of the above commands deploy to `myproject-dev.elasticbeanstalk.com`

### Environment Variables

You can specify environment variables that will be added to the system
properties of the running application, per beanstalk environment:
```clojure
:aws
{:beanstalk
 {:environments
  [{:name "dev"
    :cname-prefix "myapp-dev"
    :env {"DATABASE_URL" "mysql://..."}}]}}
```

If the environment variable name is a keyword, it is upper-cased and
underscores ("_") are substituted for dashes ("-"). e.g.
`:database-url` becomes `"DATABASE_URL"`.

Note that they will only be visible through System/getProperty and *NOT* System/getenv.

### Choosing an alternate stack

The default stack chosen is 32bit Amazon Linux running Tomcat 7. You
can customize the stack used:

    :aws {:beanstalk {:stack-name "64bit Amazon Linux running Tomcat 7"}}

The [full list][4] of available stacks that you are likely to use:

* 32bit Amazon Linux running Tomcat 7
* 64bit Amazon Linux running Tomcat 7
* 32bit Amazon Linux running Tomcat 6
* 64bit Amazon Linux running Tomcat 6

### Configuring instance type, autoscaling, VPC, SSH, AMI, SSL

You can customize many [other settings][5] on a per beanstalk environment
basis with an options key:

    :aws
    {:beanstalk
     {:environments
      [{:name "dev"
        :options {"aws:autoscaling:asg" {"MinSize" "1" "MaxSize" "1"}
                  "aws:autoscaling:launchconfiguration" {"InstanceType" "m1.medium"
                                                         "EC2KeyName" "mykey"
                                                         "ImageId" "ami-cbab67a2"}}}]}}

### Configuring the application tier ###

Amazon recently added support for [worker tiers][6], which are useful for running background tasks.
The default stack is built for a web application. To deploy as a worker, supply the following options
for the `:app-tier` key.

    :aws
    {:beanstalk
     {:app-tier {:name "Worker" :type "SQS/HTTP" :version "1.0"}}}

### S3 Buckets

[Amazon Elastic Beanstalk][1] uses
[Amazon Simple Storage Service (S3)][3] to store the versions
of the application. By default lein-beanstalk uses
`lein-beanstalk.<project-name>` as the S3 bucket name.

To use a custom bucket, specify it in the `project.clj` file:
```clojure
:aws {:beanstalk {:s3-bucket "my-private-bucket"
                  ...}}
```
### Regions

You can specify the AWS region of to deploy the application to through
your `project.clj` file:
```clojure
:aws {:beanstalk {:region "eu-west-1"}}
```

The following regions are recognized:

* `us-east-1` (default)
* `ap-northeast-1`
* `eu-west-1`
* `us-west-1`
* `us-west-2`


## Trouble-Shooting

Q: Why does my deployed web application still shows up as 'red' in the
Elastic Beanstalk console?

A: Elastic Beanstalk sends a HTTP `HEAD` request to '/' to check if
the application is running. Simply add the necessary handling to the
application. e.g. for Compojure add
```clojure
(HEAD "/" [] "")
```

## Credit

This plugin was originally written by [weavejester](https://github.com/weavejester).

We've also incorporated some pull requests that we needed from contributors to that project:

* [Allow users to supply a WAR file while deploying](https://github.com/weavejester/lein-beanstalk/pull/27) by unknown
* [Note System/getProperty must be used to access environment variables](https://github.com/weavejester/lein-beanstalk/pull/29) by @osbert
* [Document how to choose an alternate stack and the likely choices](https://github.com/weavejester/lein-beanstalk/pull/30) by @osbert
* [Support passthrough of additional ConfigurationOptionSettings](https://github.com/weavejester/lein-beanstalk/pull/31) by @osbert

We also welcome your contributions and will do our best to keep this repo updated.

[1]: http://aws.amazon.com/elasticbeanstalk
[2]: http://aws.amazon.com
[3]: http://aws.amazon.com/s3
[4]: http://docs.aws.amazon.com/elasticbeanstalk/latest/APIReference/API_ListAvailableSolutionStacks.html
[5]: http://docs.aws.amazon.com/elasticbeanstalk/latest/dg/command-options.html
[6]: http://aws.typepad.com/aws/2013/12/background-task-handling-for-aws-elastic-beanstalk.html