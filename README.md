# lein-beanstalk

Leiningen plugin for Amazon's [Elastic Beanstalk][1]

**Note: this plugin has yet to be released!**

## Prerequisites

You will need an [Amazon Web Services][2] account, and know your
account key and secret key. 

You will also need to be signed up for Elastic Beanstalk.

## Basic Configuration

To use lein-beanstalk, you'll need to add a few additional values to
your `project.clj` file.

First, add lein-beanstalk as a development dependency:

    :dev-dependencies [[lein-beanstalk "0.1.0"]]

Then add an `:aws` key with your AWS keys and Elastic beanstalk
environments:

    :aws {:access-key "XXXXXXXXXXXXXXXXXX"
          :secret-key "YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"}
          :beanstalk {:environments [myapp-dev]}}

You should now be able to deploy your application to the Amazon cloud
using the following command:

    lein beanstalk deploy myapp-dev

### Info

To get information about our environment  execute

    $ lein beanstalk info myapp-dev
    Environment Id   : e-lm32mpkr6t
    Application Name : myapp
    Environment Name : myapp-dev
    Description      : Default environment for the myapp application.
    URL              : myapp.elasticbeanstalk.com
    LoadBalancer URL : awseb-myapp-46156215.us-east-1.elb.amazonaws.com
    Status           : Ready
    Health           : Green
    Current Version  : First Release
    Solution Stack   : 32bit Amazon Linux running Tomcat 6
    Created On       : Tue Feb 08 08:01:44 EST 2011
    Updated On       : Tue Feb 08 08:05:01 EST 2011


## Trouble-Shooting

Q: Why does my deployed web application still shows up as 'red' in the
Elastic Beanstalk console?

A: Elastic Beanstalk sends a HTTP `HEAD` request to '/' to check if
the application is running. Simply add the necessary handling to the
application. e.g. for Compojure add

    (HEAD "/" [] "")

[1]: http://aws.amazon.com/elasticbeanstalk
[2]: http://aws.amazon.com
