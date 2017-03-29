# Fejoa: The Portable Privacy Preserving Cloud

Fejoa is a data sharing platform to share data with contacts at the same or a different service provider. Fejoa allows users to migrate their data/account to a different service provider without losing their contacts.

To preserve the privacy of users, the data in Fejoa is concealed from the service provider by employing encryption techniques.

Find more information on: [https://czeidler.gitlab.io/fejoapage](https://czeidler.gitlab.io/fejoapage/)

## Build

1. Clone the source code.
2. Open the source code in your favorite IDE e.g. [IntelliJ IDEA](https://www.jetbrains.com/idea/).
3. Build the project or run the tests from within the IDE.

### Build with Maven

Build all Jars:

    mvn package

To skip the tests:

    mvn package -Dmaven.test.skip=true

To build a special component:

    mvn package -pl core -Dmaven.test.skip=true

## Fejoa Server:
To just build the Fejoa server:

    mvn package -pl server -Dmaven.test.skip=true

To start the Fejoa server execute the file server-1.0-SNAPSHOT-jar-with-dependencies.jar
For example:

    java -jar server-1.0-SNAPSHOT-jar-with-dependencies.jar -h localhost -p 8180 -d dataDir

This will start the server on localhost and stores all user data in the "dataDir" directory.

### Test GUI
There is a Fejoa GUI (mostly for testing). The test GUI is located in:

    javafxgui/src/test/java/gui/ClientGui.java

Note that the test GUI starts it's own test server on localhost:8180.
When creating a new account use this url.


