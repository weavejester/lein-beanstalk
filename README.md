# lein-beanstalk

Leiningen plugin for Amazon [Elastic Beanstalk](http://aws.amazon.com/elasticbeanstalk/).


## Usage

In the following we will go through all the steps needed to deploy an
application to Amazon Elastic Beanstalk. I assume you already have a
Amazon Web Services account and signed up for Elastic Beanstalk.

### Create Elastic Beanstalk Application

First we need to create an Elastic Beanstalk application. In the
Elastic Beanstalk context an application is a logical collection
of AWS Elastic Beanstalk components, including environments,
versions, and environment configurations.
The other thing to remember is that a versioned instance of a WAR file
running on Amazon EC2 is called an environment.

With that in mind, lets get started. Creating an application is
currently a manual step, which will be automated at a later point.

1. Log into the AWS Management Console and select the Elastic Beanstalk tab.
2. Choose `Upload your own application.
3. Select an application name.
4. Upload your existing application as the application source.

After a while we should have a running version of our application on
Elastic Beanstalk, i.e. an enviroment. We can easily test it, by
enpanding the 'Environment Details' and clicking on the URL.

For additonal information see the AWS Elastic Beanstalk [Getting Started Guide](http://docs.amazonwebservices.com/elasticbeanstalk/latest/gsg/).

### Update Application

Now we will update our application using lein-beanstalk.

First, we need to modify the `project.clj` file of our application.
Add `lein-beanstalk` as a development dependency to it

    :dev-dependencies [[lein-beanstalk "0.1.0"]]

then define the service using your credentials

    :aws {:access-key "1234567890ABCDEFGHIJ"
          :secret-key "3kjebBVJEUEBJVKwjbf+2ibjwuvKJbebvdbdsvhd"}
          :s3location "elasticbeanstalk-us-east-1-3934573484
          :beanstalk {:environments ["myapp-dev"]}}

`s3location` is the Amazon S3 object/bucket that contains the deployable
code, the war file in our case. It was automagically created when we
created and launched our application. It find out what the bucket name
is, select the 'S3' tab in the AWS Management Console and under
'Buckets' find the entry starting with `"elasticbeanstalk`. That
bucket will contain all versions of the application as we update it..

With that out of the way, we can rollout a new version of our
app.

    $ lein beanstalk deploy myapp-dev

This creates a new version and deploys it to the myapp-dev environment.

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

Granted, this is not very useful right now and needs to be improved in
the future.


## Trouble-Shooting

Q: Why does my deployed web application still shows up as 'red' in the
Elastic Beanstalk console?

A: Elastic Beanstalk sends a HTTP `HEAD` request to '/' to check if
the application is running. Simply add the necessary handling to the
application. E.g. for Compojure add

    (HEAD "/" [] "")


## References

* Amazon: [AWS Elastic Beanstalk](http://aws.amazon.com/elasticbeanstalk/) - Easy to begin, Impossible to outgrow.
* Amazon: [AWS Elastic Beanstalk Getting Started Guide](http://docs.amazonwebservices.com/elasticbeanstalk/latest/gsg/) - First Steps
* Hagelberg, Phil: [Leiningen](http://github.com/technomancy/leiningen) - A build tool for Clojure designed to not set your hair on fire.
* Hickey, Rich: [Clojure](http://clojure.org/) - A dynamic programming language that targets the Java Virtual Machine.
* McGranaghan, Mark: [Ring](http://github.com/mmcgrana/ring) - Clojure web application library: abstracts HTTP to allow modular and concise webapps.
* Reeves, James: [lein-ring](http://github.com/weavejester/lein-ring) - Ring plugin for Leiningen.


## ToDo

- Add task to terminate enviroment
- Create Elastic Beanstalk application (automatically or via own task)
- Add task to delete Elastic Beanstalk application
- Better application/environment info
- Support 64-bit solution stack
- Support configuration templates


## License

Copyright (C) 2011 James Reeves (weavejester)

Distributed under the Eclipse Public License, the same as Clojure.
