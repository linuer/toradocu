# Toradocu: automated generation of test oracles from Javadoc documentation

[![Build Status](https://travis-ci.org/albertogoffi/toradocu.svg?branch=master)](https://travis-ci.org/albertogoffi/toradocu)

Toradocu generates test oracles from the Javadoc documentation of a
class. Toradocu is described in the paper
[*Automatic Generation of Oracles for Exceptional Behaviors*](http://star.inf.usi.ch/star/papers/16-issta-toradocu.pdf)
by Alberto Goffi, Alessandra Gorla, Michael D. Ernst, and Mauro Pezzè (presented
at [ISSTA 2016](https://issta2016.cispa.saarland)).

Toradocu takes the source code of a class as input and produces a set of
[aspects](https://eclipse.org/aspectj/) that can be used as test oracles.


## Building Toradocu
To compile Toradocu run the command: `./gradlew shadowJar`

This will create the file
`build/libs/toradocu-0.1-all.jar`.

Building Toradocu requires Java JDK 1.8+.


## Running Toradocu
Toradocu is a command-line tool.

To get the list of all command-line parameters, execute

      java -jar build/libs/toradocu-0.1-all.jar --help

A typical Toradocu invocation looks like this:

    java -jar toradocu-1.0-all.jar \
       --target-class mypackage.MyClass \
	   --test-class mypackage.MyTest \
	   --source-dir project/src \
	   --class-dir project/bin \
       --aspects-output-dir aspects


## Using Toradocu Aspects
With those options, Toradocu generates [AspectJ aspects](https://eclipse.org/aspectj/) in the
directory specified with the option `--aspects-output-dir`. In the aspects output directory,
Toradocu places the source code of the aspects and an `aop.xml` that lists the generated aspects
and that is used by the AspectJ compiler.

Aspects generated by Toradocu are standard AspectJ aspects and can be used to instrument an
existing test suite. This is done by using the AspectJ compiler to weave the source files
under test with the aspects generated by Toradocu.

To augment an existing test suite with Toradocu's oracles you have to:

1. Generate the aspects with Toradocu.
2. Compile the generated aspects.
3. Weave the existing test suites and the system under test.
4. Run the weaved test suite.

To compile the generated aspects you can use `javac`. Just be sure that
[JUnit](http://junit.org/junit4/), the AspectJ weaver, and your system under
test are on the classpath.

To weave the existing test suite and the system under test, you can use the
[AspectJ compiler](https://eclipse.org/aspectj/doc/next/devguide/ajc-ref.html).

Run the weaved test suite as as you would normally.

Please refer to the [AspectJ documentation](https://eclipse.org/aspectj/doc/released/devguide/ajc-ref.html)
for more information.


## Tutorial
This step-by-step tutorial illustrates how Toradocu can be used to discover
faults in a software system under test. This tutorial covers the test of a toy
class `net.Connection` that represents a network connection. The class offers
the method `open` to establish a new connection.

1. Compile Toradocu:  `./gradlew shadowJar`

2. Move to the tutorial directory: `cd tutorial`

   The directory `tutorial/src` contains the source code of the system under test,
   while the directory `tutorial/test` contains the source code of the test
   suite.

   the method `net.Connection#open` is supposed to throw an
   IllegalStateException if invoked on an open connection. The method
   `net.Connection#open` is called by the test case `net.ConnectionTest#open`,
   but that test case does not verify that IllegalStateException is called.

3. Download the libraries required to compile and run the system under test, its
   test suite, and the aspects generated by Toradocu.
   ```
   wget -O junit-4.12.jar http://search.maven.org/remotecontent\?filepath\=junit/junit/4.12/junit-4.12.jar
   wget -O hamcrest-core-1.3.jar http://search.maven.org/remotecontent?filepath=org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar
   wget -O aspectjtools-1.8.9.jar http://central.maven.org/maven2/org/aspectj/aspectjtools/1.8.9/aspectjtools-1.8.9.jar
   wget -O aspectjweaver-1.8.9.jar http://central.maven.org/maven2/org/aspectj/aspectjweaver/1.8.9/aspectjweaver-1.8.9.jar
   wget -O aspectjrt-1.8.9.jar http://central.maven.org/maven2/org/aspectj/aspectjrt/1.8.9/aspectjrt-1.8.9.jar
   ```

4. Compile the source code of the system under test: `javac src/net/Connection.java`

5. Compile and execute the test suite
   ```
   javac -cp junit-4.12.jar:src test/net/ConnectionTest.java
   java -cp junit-4.12.jar:hamcrest-core-1.3.jar:test:src org.junit.runner.JUnitCore net.ConnectionTest
   ```
   The test suite terminates without failures, printing `OK (1 test)`.

6. Generate the aspects with Toradocu in the folder `aspects`
   ```
   java -jar ../build/libs/toradocu-1.0-all.jar \
   --target-class net.Connection \
   --test-class net.ConnectionTest \
   --source-dir src \
   --class-dir src \
   --aspects-output-dir aspects
   ```
   Toradocu prints on the standard output the output of the condition translation phase.

7. Compile the generated aspects and weave them into the test suite of the system under test
   ```
   javac -g -cp aspectjrt-1.8.9.jar:src:junit-4.12.jar aspects/Aspect_1.java
   java -cp aspectjtools-1.8.9.jar:aspectjweaver-1.8.9.jar:aspectjrt-1.8.9.jar:src org.aspectj.tools.ajc.Main -inpath aspects:test -outjar weaved_testsuite.jar -showWeaveInfo
   ```

8. Run the test suite augmented with Toradocu's oracles
   ```
   java -cp junit-4.12.jar:hamcrest-core-1.3.jar:src:weaved_testsuite.jar:aspectjrt-1.8.9.jar org.junit.runner.JUnitCore net.ConnectionTest
   ```
   The test suite execution terminates with one failure:

   ```
   There was 1 failure:
   1) open(net.ConnectionTest)
   java.lang.AssertionError: Triggered aspect: Aspect_1 (ConnectionTest.java:13) -> Failure: Expected exception not thrown. Expected exceptions were: java.lang.IllegalStateException
   ```

   The report says that an IllegalStateException was expected, but wrongly the method `net.Connection#open` did not raise any exception.

## Contributing to Toradocu
Information for contributors can be found on the [wiki pages](https://github.com/albertogoffi/toradocu/wiki/Developer-Notes).
