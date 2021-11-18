# Sample app for servlet-scopes library

Few trivial servlets and websockets to test basic functionality of `servlet-scopes` lib. Powered by embedded Jetty.


## BUILDING AND RUNNIG

### Prerequisites and 1 time setup
1. java 11 is required to build the app (newer versions will probably work also).
1. if you are using a SNAPSHOT version, build and install `servlet-scopes`: `cd ..; ./mvnw install; cd -`

### Build and run
1. build the project with `./mvnw package`
1. start Jetty with `java -server -jar target/servlet-scopes-sample-1.0-SNAPSHOT-jar-withependencies.jar`
1. point your browser to http://localhost:8080 to use the apps
1. when done, you can stop the server by pressing `CTRL+C` on its console or sending it `SIGINT` other way
