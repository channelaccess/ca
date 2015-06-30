[![Build Status](https://travis-ci.org/channelaccess/ca.svg?branch=master)](https://travis-ci.org/channelaccess/ca)

# Overview
__ca__ is a pure Java Channel Access implementation. __ca__ is the easiest way in Java to access Channel Access channels.

# Usage

## Prerequisites

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

## Usage

Create simple channel:

```java
try (Context context = new Context())
{
  Channel<Double> channel = context.createChannel("MY_CHANNEL", Double.class);
  channel.connect();
  System.out.println(channel.get());
  channel.close();
}
```

A full usage example can be found at `src/test/java/org/epics/ca/test/Example.java`.


# Development
The project can be build via *gradle* by executing the provided wrapper scripts as follows:
 * Linux: `./gradlew build`
 * Windows: `gradlew.bat build`

There is no need to have *gradle* installed on your machine, the only prerequisite for building is a Java >= 8 installed.

_Note:_ The first time you execute this command the required jars for the build system will be automatically downloaded and the build will start afterwards. The next time you execute the command the build should be faster.
