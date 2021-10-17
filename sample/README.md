# Sample app for servlet-scopes library

[ChatEndpoint](src/main/java/pl/morgwai/samples/servlet_scopes/ChatEndpoint.java) and [ServletContextListener](src/main/java/pl/morgwai/samples/servlet_scopes/ServletContextListener.java) are the most interesting files.


## BUILDING AND RUNNIG WITH DEMO JETTY CONFIG

### Prerequisites and 1 time setup
1. java 11 is required to build the app (newer versions will probably work also)
1. download Jetty from https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/ and extract it to a folder of choice
1. export `JETTY_HOME` env var pointing to the above folder: `export JETTY_HOME=/path/to/folder/where/jetty/was/extracted`
1. if you are using a SNAPSHOT version, build and install `servlet-scopes`: `cd ..; ./mvnw install; cd -`
1. by default the project is built with maven, if you want to use gradle, run `./generate-build.gradle.sh`
1. go to `src/main/jetty` subfolder and execute either `ln -s ../../../target/servlet-scopes-sample-1.0-SNAPSHOT.war servlet-scopes-sample.war` or `ln -s ../../../build/libs/servlet-scopes-sample-1.0-SNAPSHOT.war servlet-scopes-sample.war` depending if you use gradle or maven

### Build and run
1. build the project with either `./mvnw package` or `./gradlew build`
1. go to `src/main/jetty` subfolder and start Jetty with `java -server -jar ${JETTY_HOME}/start.jar`
1. point your browser to http://localhost:8080 to use the apps
1. when done, you can stop the server by pressing `CTRL+C` on its console or sending it `SIGINT` other way
