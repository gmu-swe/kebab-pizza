# kebab-pizza

This is a maven extension to make it "easy" to integrate off-the-shelf regression testing tools in maven projects for the purpose of running evaluations.

Installing
----
`mvn install` at the top level, then copy `maven-extension/target/kp-maven-extension-1.0-SNAPSHOT.jar` to your `$MAVEN_DIRECTORY/lib/ext` directory. This will make KebabPizza active on every maven run! You might find it makes the most sense to make a new copy of maven and install KP in that other maven... then when you want KP to run, use that other maven.

General options
-----
These environmental variables can be used regardless of the other tooling applied. For boolean variables, setting the variable to any value will set it to be true.

* `KP_FORK_PER_TEST` Run each test in a fresh JVM (forkMode=perTest)
* `KP_RECORD_TESTS` Record test results to Firebase/MySQL
* `KP_FAIL_ON_FAILED_TEST` Break the build when a test fails (otherwise we make sure that it never breaks)
* `KP_ARGLINE` Additional flags to pass on the argument line when tests run
* `KP_DEPENDENCIES` Comma-separated additional dependencies to include for the test listener when it runs, in the format `group:artifact:version`

Jacoco Usage
-----
```
KP_JACOCO_OUTPUT_FILE=<outputFile> KP_JACOCO=true KP_JACOCO_EXEC_FILE=<jacocoExecFileToOutputTo> ../../apache-maven-3.5.4/bin/mvn org.jacoco:jacoco-maven-plugin:0.7.9:prepare-agent verify org.jacoco:jacoco-maven-plugin:0.7.9:report
```
Can also set `KP_JACOCO_PER_CLASS=true` and then coverage will be recorded per-test.

Pit Usage
------
KP_PIT=true ../../apache-maven-3.5.4/bin/mvn verify

We will automatically mutate ALL files in target/classes of all modules
We let Pit decide which tests to run though
We turn off surefire/failsafe and make sure tests get compiled, pre-test modules run, etc.
Can also do -Dverbose=true to get Pit's verbose logging
