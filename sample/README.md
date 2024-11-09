# Sample app for servlet-scopes library

Few trivial servlets and websockets to test basic functionality of `servlet-scopes` lib. Available as a portable war or an executable jar powered by embedded Jetty.


## BUILDING AND RUNNING

### Prerequisites and 1 time setup
1. java 11 is required to build the app (newer versions will probably work also).
1. if you are using a SNAPSHOT version, build and install `servlet-scopes` first: `cd ..; ./mvnw install; cd -`
1. to deploy the war to a stand-alone Jetty using provided sample config, [download and extract Jetty distribution](https://www.eclipse.org/jetty/download.php) and export `JETTY_HOME` env var pointing to the folder where Jetty was extracted.

### Build and run using a stand-alone Jetty
1. build the project: `./mvnw package`
1. start stand-alone Jetty: `cd src/main/jetty/ && java -server -jar ${JETTY_HOME}/start.jar ; cd -`

### Build and run using an embedded Jetty
1. generate alternative `pom.xml` file: `grep -v 'EMBEDDED-REMOVE' pom.xml >pom-embedded-jetty.xml`
1. build the project: `./mvnw -f pom-embedded-jetty.xml package`
1. start embedded Jetty: `java -server -jar target/servlet-scopes-sample-1.0-SNAPSHOT-jar-with-dependencies.jar`

### Usage
Point your browser to [http://localhost:8080/test](http://localhost:8080/test) to use the apps, when done, you can stop the server by pressing `CTRL+C` on its console or sending it a `SIGINT` another way.
