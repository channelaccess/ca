# Overview [![Build Status](https://travis-ci.org/channelaccess/ca.svg?branch=master)](https://travis-ci.com/channelaccess/ca)
__ca__ is a pure Java Channel Access client implementation. __ca__ is the easiest way in Java to access Channel Access channels.

## Features
* Simplicity.
* Use of Java type system.
* Synchronous and asynchronous operations for get, put, connect.
* Efficient handling of parallel operations without the need to use threads.
* Chaining of actions/operations, e.g. set this, then set that, ...
* Easily get additional metadata to value: Timestamp, Alarms, Graphic, Control.
* Supports channel monitoring including mask capability to select events-of-interest (eg value, log, alarm 
and/or property changes).
* Supports the following listeners: ConnectionListener, AccessRightsListener.

## Compatibility

Implements CA protocol specified in version 4.13 of the Channel Access Protocol Specification. The CA protocol
specification is distributed in the documents associated with each EPICS base release. For developer convenience a 
PDF of the current version is available in the [documents](docs) area of this project. 

# Installation

The current Travis build status of the latest release in the GitHub repository is:

__ca__ is available on Maven Central. It can be easily retrieved by Maven or Gradle as follows:

Maven:

```xml
<dependency>
  <groupId>org.epics</groupId>
  <artifactId>ca</artifactId>
  <version>1.2.2</version>
</dependency
```

Gradle:

```gradle
compile 'org.epics:ca:1.2.1'
```

__Note:__ To be able to retrieve the current snapshot version you have to configure the following repository:

```gradle
repositories {
  maven {
    url 'http://oss.sonatype.org/content/repositories/snapshots'
  }
}
```

# Usage

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

After creating the channel object the channel needs to be connected. There is a synchronous and asynchronous way to do so. The synchronous/blocking way is to call:

```java
channel.connect()
```

The asynchronous way is to call:

```java
`connectAsync()`
```

`connectAsync()` will return a CompletableFuture. To check whether the connect was successful call `.get()` on it. The synchronous way to connect will block until the channel can be connected. If you want to specify a timeout for a connect use the asynchronous connect as follows:

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

### Monitors
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

Internally the CA library uses a monitor notification engine to deliver the  notifications received from the
remote IOCs to the local Consumer. The properties of this engine are configurable using either a system 
property or via a ```Properties``` object passed to the Context at the time of construction.

The property, named ```CA_MONITOR_NOTIFIER_IMPL```, can be used as follows:

```java
final Properties contextProperties = new Properties();
contextProperties.setProperty( "CA_MONITOR_NOTIFIER_IMPL", <my_monitor_config> );
try ( final Context context = new Context( contextProperties ) ) {
   ... create and use your monitor as normal here...
}
```

The configuration string is used as follows:

|Configuration String                                                           | Type            | Default Buffer Size  | Default Number of Consumer Notification Threads| Additional Comment |
|-------------------------------------------------------------------------------|-----------------|----------------------|------------------------------------------------|----------------------------------------------------|
|"BlockingQueueMultipleWorkerMonitorNotificationServiceImpl {,threads}{,bufsiz}"| Non-lossy       | Integer.MAX_VALUE    | 16                                             | Threads and buffer size are configurable.          |
|"BlockingQueueSingleWorkerMonitorNotificationServiceImpl {,threads}{,bufsiz}"  | Non-lossy       | Integer.MAX_VALUE    |  1                                             | Threads parameter is ignored and fixed to 1.       |
|"DisruptorOldMonitorNotificationServiceImpl"                                   | Lossy           | 2                    |  1                                             | Limited to < 1000 monitors per client application. |
|"DisruptorNewMonitorNotificationServiceImpl"                                   | Lossy           | 2                    |  1                                             | Experimental. Attempts to improve on the old one.  |
|"StripedExecutorServiceMonitorNotificationServiceImpl {,threads}"              | Non-lossy       | Integer.MAX_VALUE    | 10                                             | Uses Heinz Kabbutz StripedExecutorService.         |

Note: 

1. The configuration of the monitor notification engine is an experimental feature. In the future it is possible 
and/or likely that the library will offer only a single engine, fully configurable to meet the needs of all 
client applications.   
1. The ```BlockingQueueMultipleWorkerMonitorNotificationServiceImpl``` and ```StripedExecutorServiceMonitorNotificationServiceImpl```
notification engines provide optional configuration parameters allowing the size of the notification buffer and number 
of consumer notification threads to be configured. 
1. Further details on the requirements for the monitor notification engine and its performance are available in the
   following [MONITOR_INFO.md](https://github.com/channelaccess/ca/blob/master/MONITOR_INFO.md) file.

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


### ConnectionState
The channels connection state can be checked as follows:

```java
channel.getConnectionState()
```

## Channels
The utility class `Channels` provides various convenience functions to create, close and operate on channels.

### Create
To create channels `Channels` provides these functions:

```java
// Create and connect channel
Channel<String> channel1 = Channels.create(context, "name", String.class);

// Create and connect channel
Channel<String> channel2 = Channels.create(context, new ChannelDescriptor<String>("name", String.class));

// Create and connect multiple channels at once
List<ChannelDescriptor<?>> descriptors = new ArrayList<>();
descriptors.add(new ChannelDescriptor<String>("name", String.class));
descriptors.add(new ChannelDescriptor<Double>("name_double", Double.class));
List<Channel<?>> channels = Channels.create(context,  descriptors);
```

All of these function will __create__ and __connect__ the specified channels. a

### WaitForValue
For waiting until a channel reaches a specified value `Channels` provide following functions:

```java
waitForValue(channel, "value")

// Use custom comparator for checking what is equal ...
Comparator<String> comparator = ...
waitForValue(channel, "value", comparator)
```

Both functions are also available in an __async__ version. Instead of blocking they return a CompletableFuture.

```java
CompletableFuture<String> future = waitForValue(channel, "value")
// ... do something\
future.get();

// Use custom comparator for checking what is equal ...
Comparator<String> comparator = ...
CompletableFuture<String> future1 = waitForValue(channel, "value", comparator)
// ... do something
future1.get()
```

## Annotations
Ca provides the annotation, __@CaChannel__,  to annotate channel declarations within a class. While using the `Channels` utility class these annotations can be used to easily and efficiently create these channels.

All that needs to done is, to annotate the channel declarations as follows:
```java
class AnnotatedClass {
		@CaChannel(name="adc01", type=Double.class)
		private Channel<Double> doubleChannel;

		@CaChannel(name="adc01", type=String.class)
		private Channel<String> stringChannel;

		@CaChannel(name={"adc01", "simple"}, type=String.class)
		private List<Channel<String>> stringChannels;

		public Channel<Double> getDoubleChannel() {
			return doubleChannel;
		}
		public Channel<String> getStringChannel() {
			return stringChannel;
		}
		public List<Channel<String>> getStringChannels() {
			return stringChannels;
		}
	}
```

Afterwards the channels can be created via `Channels` as follows:

```java
AnnotatedClass object = new AnnotatedClass();
Channels.create(context, object);
```

To close all annotated channels use:

```java
Channels.close(object);
```

As channel names should not be hardcoded within an annotation, the name of a channel may contain multiple macros (e.g. `@CaChannel(name="adc${MACRO1}", type=String.class)`). While creating the channels a map of macros need to be passed to the `Channels.create` function.

```java
Map<String,String> macros = new HashMap<>();
macros.put("MACRO1","01");
AnnotatedClass object = new AnnotatedClass();
Channels.create(context, object, macros);
```

Macro names are __case sensitive__!

## Examples

Create simple channel:

```java
try (Context context = new Context())
{
  try(Channel<Double> channel = Channels.create(context, "MY_CHANNEL", Double.class)){
    System.out.println(channel.get());
  }
}
```

An extended usage example can be found at [src/test/java/org/epics/ca/test/Example.java](src/test/java/org/epics/ca/test/Example.java).


# Development
The project can be build via *gradle* by executing the provided wrapper scripts as follows:
 * Linux: `./gradlew build`
 * Windows: `gradlew.bat build`

There is no need to have *gradle* installed on your machine, the only prerequisite for building is a Java >= 8 installed.

__Note:__ The first time you execute this command the required jars for the build system will be automatically downloaded and the build will start afterwards. The next time you execute the command the build should be faster.

## Maven Central
To push the latest version to Maven Central (via the OSS Sonatype Nexus Repository) use

```bash
./gradlew uploadArchives
```

To be able to do so you need to have your ~/.gradle/gradle.properties file in place with your Sonatype username/password as well you need to be part of the group *org.epics*

For further information on using gradle to upload binary releases to the Sonatype OSS Nexus Repository please see the document [here.](https://central.sonatype.org/pages/gradle.html)