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

## Dependencies

__ca__ is available on Maven Central. It can be easily retrieved by Maven or Gradle as follows:

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

## Context

To be able to create channels a Context need to be created. The context is a container for channels. If the context is closed also all channels created with the context will be closed.

This is how to create a context:

```java
Context context = new Context()
```

A context accepts several properties. Properties can be set at Context creation time as follows:

```java
Properties properties = new Properties();
properties.setProperty(Context.Configuration.EPICS_CA_ADDR_LIST.toString(), "10.10.10.255");
new Context(properties);
```

All possible properties are available in the `Configuration` enumeration inside the Context class. The available properties are:

| Property | Desciption |
|----|----|
|EPICS_CA_ADDR_LIST|Address list to search the channel|
|EPICS_CA_AUTO_ADDR_LIST|Automatically build up search address list|
|EPICS_CA_CONN_TMO||
|EPICS_CA_BEACON_PERIOD||
|EPICS_CA_REPEATER_PORT||
|EPICS_CA_SERVER_PORT|Channel access server port|
|EPICS_CA_MAX_ARRAY_BYTES|Maximum size in bytes of an array/waveform - see note below!|

_Note:_ In contrast to other Channel Access libraries EPICS_CA_MAX_ARRAY_BYTES is set to unlimited by default. Usually there is no reason to set this property. Memory is dynamically acquired as needed.

The context need to be closed at the end of the application via:

```java
context.close();
```

_Note:_ As Context implements `AutoCloseable` you can also use

```java
try(Context context = new Context){
  // Code
}
```

## Channel
To create a channel use:

```java
Channel<Double> channel = context.createChannel("MY_CHANNEL", Double.class);
```

At creation time of the channel its type need to be defined. If you want to have a generic type of the channel (i.e. you want to use the type set on the server) use:

```java
Channel<Object> channel = context.createChannel("ARIDI-PCT:CURRENT", Object.class);
```

When getting a value from the channel you will get the correct/corresponding Java type that maps to the type set on the server.

After creating the channel object the channel needs to be connected. There is a synchronous and asynchronous way to do so. The synchronous/blocking way is to call `connect()`. The asynchronous way is to call `connectAsync()`. `connectAsync()` will return a CompletableFuture. To check whether the connect was successful call `.get()` on it. The synchronous way to connect will block until the channel can be connected. If you want to specify a timeout for a connect use the asynchronous connect as follows:

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

### Get / Put
After creating a channel you are able to get and put values via the `get()` and `put(value)` methods.

To put a value in a fire and forget style use `putNoWait(value)`. This method will put the value change request on the network but does not wait for any kind of acknowledgement.

```java
// Get value
double value = channel.get();
// Set value
channel.put(10.0);
// Set value (best effort style)
channel.putNoWait(10.0);
```

Beside the synchronous (i.e. blocking until the operation is done) versions of `get()` and `put(value)` there are also asynchronous calls. They are named `getAsync()` and `putAsync(value)`. Both functions immediately return with a CompletableFuture for the operation. The Future can be used to wait at any location in the application and to wait for the completion of the operation and to retrieve the final value of the channel.

Example asynchronous get:

```java
CompletableFuture<Double> future = channel.getAsync();
CompletableFuture<Double> future2 = channel2.getAsync();

// do something different ...
doSomething();
// ... or simply sleep ...
Thread.sleep(1000);
// ... or simply do nothing ...


double value = future.get();
double value2 = future2.get();
```

Example asynchronous put:

```java
CompletableFuture<Status> future = channel.putAsync(1.0); // this could, for example start some move of a motor ...
CompletableFuture<Status> future2 = channel2.putAsync(5.0);

/ do something different ...
doSomething();
// ... or simply sleep ...
Thread.sleep(1000);
// ... or simply do nothing ...


future.get(); // this will return a status object that can be queried if put was successful
future2.get(); // this will return a status object that can be queried if put was successful                                                                                                                                                                                                                                                            
```

### Metadata
If you want to retrieve more metadata besides the value from the channel you can request this by specifying the type of metadata with the get call. For example if you also want to get the value modification/update time besides the value from the cannel use:

```java
channel.get(Timestamped.class)
```

Ca supports all metadata types Channel Access provides, namely `Timestamped`, `Alarm`, `Graphic` and `Control`.

|Metadata Type| Metadata|
|----|----|
|Timestamped| seconds, nanos|
|Alarm| alarmStatus, alarmSeverity|
|Graphic| alarmStatus, alarmSeverity, units, precision, upperDisplay, lowerDisplay, upperAlarm, lowerAlarm, upperWarning, lowerWarning|
|Control| alarmStatus, alarmSeverity, units, precision, upperDisplay, lowerDisplay, upperAlarm, lowerAlarm, upperWarning, lowerWarning, upperControl, lowerControl|

### Monitor
If you want to monitor a channel you can attach a monitor to it like this:

```java
Monitor<Double> monitor = channel.addValueMonitor(value -> System.out.println(value));
```

To close a monitor use:

```java
monitor.close()
```

Again if you like more metadata from the monitor you can specify the type of metadata you are interested in.

```java
Monitor<Timestamped<Double>> monitor =
    channel.addMonitor(
        Timestamped.class,
            value -> { if (value != null) System.out.println(new Date(value.getMillis()) + " / " + value.getValue()); }
            );
```

### Listeners
A channel can have Access Right and Connection listeners. These two types of listeners are attached as follows.



```java
Listener connectionListener = channel.addConnectionListener((channel, state) -> System.out.println(channel.getName() + " is connected? " + state));


Listener accessRightListener = channel.addAccessRightListener((channel, rights) -> System.out.println(channel.getName() + " is rights? " + rights));
```
To remove the listener(s), or use `try-catch-resources` (i.e. Listeners implement `AutoCloseable`) or 

```java
listener.close()
```

_Note:_ These listeners can be attached to the channel before connecting.

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
