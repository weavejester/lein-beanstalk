# lein-beanstalk

Leiningen plugin for Amazon's Elastic Beanstalk service.


## Usage

To use lein-beanstalk, add it as a development dependency to your project.clj file:

    :dev-dependencies [[lein-beanstalk "0.1.0"]]

Then define the service using your credentials

    :aws {:access-key "1234567890ABCDEFGHIJ"
          :secret-key "3kjebBVJEUEBJVKwjbf+2ibjwuvKJbebvdbdsvhd"}
          :beanstalk {:environments [:production]}}


## License

Copyright (C) 2011 James Reeves (weavejester)

Distributed under the Eclipse Public License, the same as Clojure.
