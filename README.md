[![Build Status](https://travis-ci.org/channelaccess/ca.svg?branch=master)](https://travis-ci.org/channelaccess/ca)

# Overview
__ca__ is a pure Java Channel Access implementation. __ca__ is the easiest way in Java to access Channel Access channels.

# Usage

__ca__ is available on Maven Central. It can be retrieved easily by Maven or Gradle as follows:

Maven:

```xml
<dependency>
  <groupId>org.epics</groupId>
  <artifactId>ca</artifactId>
  <version>0.2-SNAPSHOT</version>
</dependency
```

Gradle:

```gradle
compile 'org.epics:ca:0.2-SNAPSHOT'
```

To be able to retrieve the current snapshot version you have to configure the

```gradle
repositories {
  maven {
    url 'http://oss.sonatype.org/content/repositories/snapshots'
  }
}
```


# Development
The project can be build via *gradle* by executing the provided wrapper scripts as follows:
 * Linux: `./gradlew build`
 * Windows: `gradlew.bat build`

There is no need to have *gradle* installed on your machine, the only prerequisite for building this project is a Java >= 8 installed.

_Note:_ The first time you execute this command the required jars for the build system will be automatically downloaded and the build will start afterwards. The next time you execute the command the build should be faster.
