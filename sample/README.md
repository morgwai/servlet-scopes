# Sample app for servlet-scopes library

Few trivial servlets and websockets to test basic functionality of `servlet-scopes` lib. Available as a portable war or an executable jar powered by embedded Jetty.


## BUILDING AND RUNNIG

### Prerequisites and 1 time setup
1. java 11 is required to build the app (newer versions will probably work also).
1. if you are using a SNAPSHOT version, build and install `servlet-scopes` first: `cd ..; ./mvnw install; cd -`
1. to deploy the war to a stand-alone Jetty using provided sample config, [download and extract Jetty distribution](https://www.eclipse.org/jetty/download.php) and export `JETTY_HOME` env var pointing to the folder where Jetty was extracted.

### Build and run
1. build the project with `./mvnw package`
1. start either stand-alone or embedded Jetty:
    - embedded: `java -server -jar target/servlet-scopes-sample-1.0-SNAPSHOT-jar-with-dependencies.jar`
    - stand-alone: `cd src/main/jetty/ && java -server -jar ${JETTY_HOME}/start.jar ; cd -`
1. point your browser to [http://localhost:8080/test](http://localhost:8080/test) to use the apps
1. when done, you can stop the server by pressing `CTRL+C` on its console or sending it `SIGINT` other way
