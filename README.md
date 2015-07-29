[![Build Status](https://travis-ci.org/channelaccess/ca.svg?branch=master)](https://travis-ci.org/channelaccess/ca)

# Overview
__ca__ is a pure Java Channel Access client implementation. __ca__ is the easiest way in Java to access Channel Access channels.

## Features
* Simplicity
* Use of Java type system
* Synchronous and asynchronous operations for get, put, connect
* Efficient handling of parallel operations without the need to use threads
* Chaining of actions/operations, e.g. set this, then set that, ...
* Easily get additional metadata to value: Timestamp, Alarms, Graphic, Control
* Support of all listeners ChannelAccess supports: ConnectionListener, AccessRightListener, Value Listener (Monitor)



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

__Note:__ To be able to retrieve the current snapshot version you have to configure the following repository:

```gradle
repositories {
  maven {
    url 'http://oss.sonatype.org/content/repositories/snapshots'
  }
}
```

## Usage

To be able to create channels a Context need to be created. The context is a container for all channels created with it. If the context is closed also all channels created with the context will be closed.

```java
Context context = new Context()
```

The context accepts several properties. Properties can be set as follows at Context creation time:

```java
Properties properties = new Properties();
properties.setProperty(Constants.ADDR_LIST_KEY, "10.10.10.255");
new Context(properties);
```

The available properties are:
* Constants.ADDR_LIST_KEY
* Constants.AUTO_ADDR_LIST_KEY
* Constants.BEACON_PERIOD_KEY
* Constants.SERVER_PORT_KEY

* Constants.MAX_ARRAY_BYTES_KEY
* Constants.REPEATER_PORT_KEY
* Constants.CONN_TMO_KEY

_Note:_ In contrast to other Channel Access libraries MAX_ARRAY_BYTES_KEY is set to unlimited by default. Therefore usually there is no reason to set this property.


To create a channel use:

```java
Channel<Double> channel = context.createChannel("MY_CHANNEL", Double.class);
```

At creation time of the channel you need to specify what type you like for the channel.

After creating the channel object the channel explicitly needs to be connected. There is a synchronous and asynchronous way to do so. The synchronous/blocking way is to call `connect()`. The asynchronous way is to call `connectAsync()`. `connectAsync()` will return a CompletableFuture, to check whether the connect was successful call `.get()` on it. The synchronous way to connect will block until the channel can be connected. If you want to specify a timeout for a connect use the asynchronous connect as follows:

```
channel.connectAsync().get(1, java.util.concurrent.TimeUnit.SECONDS);
```

For connecting multiple channels in parallel use:

```java
Channel<Integer> channel1 = context.createChannel("adc02", Integer.class);
Channel<String> channel2 = context.createChannel("adc03", String.class);

// Wait for all channels to be connected
CompletableFuture.allOf(channel1.connectAsync(), channel2.connectAsync()).get();
```

A timeout for the multiple connect is realized the same way as with the single `connectAsync()`.


### Examples

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

An extended usage example can be found at [src/test/java/org/epics/ca/test/Example.java](src/test/java/org/epics/ca/test/Example.java).


# Development
The project can be build via *gradle* by executing the provided wrapper scripts as follows:
 * Linux: `./gradlew build`
 * Windows: `gradlew.bat build`

There is no need to have *gradle* installed on your machine, the only prerequisite for building is a Java >= 8 installed.

__Note:__ The first time you execute this command the required jars for the build system will be automatically downloaded and the build will start afterwards. The next time you execute the command the build should be faster.
