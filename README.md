# Overview [![Build Status](https://travis-ci.com/channelaccess/ca.svg?branch=master)](https://travis-ci.com/channelaccess/ca) [![codecov](https://codecov.io/gh/channelaccess/ca/branch/master/graph/badge.svg)](https://codecov.io/gh/channelaccess/ca) ![Maven Central](https://img.shields.io/maven-central/v/org.epics/ca) [ ![JCenter](https://api.bintray.com/packages/paulscherrerinstitute/hltools/org.epics%3Aca/images/download.svg) ](https://bintray.com/paulscherrerinstitute/hltools/org.epics%3Aca/_latestVersion)
The __ca__  library is a pure Java Channel Access client implementation. __ca__ strives to provide the _easiest_ 
way of accessing EPICS Channel Access channels from Java.

The notes in theis README file are aimed to assist the __users__ of the library. Developers and __maintainers__ of the library are 
invited to consult the [Developer](DEVELOPER.md) notes.

The version history and latest changes to the library are described in the [CHANGELOG](CHANGELOG.md). 

The results of the functional tests that have been performed on the latest release are available in the 
[INTEGRATION_TESTS](INTEGRATION_TESTS.md) document.

An extended usage [Example](src/main/java/org/epics/ca/examples/Example.java) is available.

## Features
* Simplicity.
* Use of Java type system.
* Synchronous and asynchronous operations for get, put, connect.
* Efficient handling of parallel operations without the need to use threads.
* Chaining of actions/operations, e.g. set this, then set that, ...
* Easily get additional metadata to value: Timestamp, Alarms, Graphic, Control.
* Supports channel monitoring, including mask capability to select events-of-interest (eg value, log, alarm and/or 
property changes).
* Supports the following listeners: ConnectionListener, AccessRightsListener.

## Compatibility

Implements CA protocol specified in __Version 4.13__ of the Channel Access Protocol Specification. 

The CA protocol specification is available on the new EPICS website [here](https://docs.epics-controls.org/en/latest/specs/ca_protocol.html).
Because links have a tendency to go out-of-date a [PDF](docs/Channel%20Access%20Protocol%20Specification.pdf) 
version is archived in the [documents](docs) area of this project. 

The __ca__ library 1.x.y series of releases are compatible with __Java 8__ and higher.

It is anticipated that the current __ca__ release range (1.3.x) will be the __last release to support Java 8__ and 
that future releases will require at least __Java 11__.

## Distribution

The __ca__ library is available as an Apache Maven package for use in Java projects which use Maven, Gradle, Ivy etc
as their build system.
 
The current distribution home of the library is [JCenter](https://bintray.com/paulscherrerinstitute/hltools/org.epics%3Aca).
Here you can find the most recent library builds. Earlier versions (up to 1.2.2) were available at 
[Maven Central](https://search.maven.org/artifact/org.epics/ca).

#### Maven Project Usage

Add the following dependency:

```xml
<dependency>
  <groupId>org.epics</groupId>
  <artifactId>ca</artifactId>
  <version>1.3.2</version>
</dependency>
```

and the following repository:

```xml
<repositories>
    <repository>
      <id>jcenter</id>
      <url>https://jcenter.bintray.com/</url>
    </repository>
</repositories>
```

#### Gradle Project Usage

Add the following dependency:

```gradle
compile 'org.epics:ca:1.3.2'
```

and the following repository:

```gradle
repositories {
    maven {
        url "https://jcenter.bintray.com" 
    }
```

# Library Usage

## Configuration

#### Library Configuration

The __ca__ library supports the following configuration variables which can be specified, once per JVM instance, by 
means of environmental variables set in the host OS, or through Java System properties passed on the command-line.

| Property Name               | Description                                                                             | Default Value |
|-----------------------------|-----------------------------------------------------------------------------------------|---------------|
| CA_LIBRARY_LOG_LEVEL        | The level at which CA library log messages will be sent to the standard output stream.  | "INFO"        | 
| CA_REPEATER_LOG_LEVEL       | The level at which CA Repeater log messages will be sent to the standard output stream. | "INFO"        | 
| CA_REPEATER_DISABLE         | Whether the CA library will start/stop a local CA Repeater instance.                    | "false"       |
| CA_REPEATER_OUTPUT_CAPTURE  | Whether to capture the output of the CA Repeater's log messages.                        | "false"       |
| CA_MONITOR_NOTIFIER_IMPL    | The configuration of the CA library monitor notification engine.                        | see below     |


#### EPICS Channel Access Protocol Configuration

The __ca__ library supports the configuration of the operating parameters of the EPICS Channel Access Protocol by
means of the "normal" configuration variables. These can be set once per context instance.

These can be specified by means of environmental variables in the host OS, through Java System properties, or by 
passing an appropriately configured properties object when creating a context. The following properties are
supported:

| Property Name            | Description                                                                                | Default Value |
|--------------------------|--------------------------------------------------------------------------------------------|---------------|
| EPICS_CA_ADDR_LIST       | The address list to be used when searching for channels.                                   | empty         | 
| EPICS_CA_AUTO_ADDR_LIST  | Automatically build up search address list by introspecting local network interfaces.      | "true"        | 
| EPICS_CA_CONN_TMO        | The UDP Beacon Message Timeout.                                                            | "30s"         |
| EPICS_CA_REPEATER_PORT   | The port to be used when communicating with the local CA Repeater instance.                | "5065"        |
| EPICS_CA_SERVER_PORT     | The port to be used when broadcasting channel search requests to the CA servers.           | "5064"        | 
| EPICS_CA_MAX_ARRAY_BYTES | The maximum size in bytes of an array/waveform.                                            | unlimited     |

_Note:_ In contrast to other Channel Access libraries EPICS_CA_MAX_ARRAY_BYTES is set to unlimited by default. Usually 
there is no reason to set this property since the memory is dynamically acquired as required.

By default the __ca__ library will discover the available EPICS channels by broadcasting search requests on all 
locally-enabled network interfaces.


#### Monitor Notification Engine Configuration

Internally the __ca__ library uses a monitor notification engine to deliver the notifications received from the remote IOCs 
to the local library users via the Java ```Consumer``` interface.

The ```CA_MONITOR_NOTIFIER_IMPL``` property can be used to configure the properties of this engine as follows:

| Configuration String                                                           | Default Buffer Size     | Default Number of Consumer Notification Threads| Additional Comment |
|--------------------------------------------------------------------------------|-------------------------|------------------------------------------------|----------------------------------------------------|
| "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl {,threads}{,bufsiz}"| Integer.MAX_VALUE       | 16                                             | Threads and buffer size are configurable.          |
| "BlockingQueueSingleWorkerMonitorNotificationServiceImpl {,threads}{,bufsiz}"  | Integer.MAX_VALUE       |  1                                             | Threads parameter is ignored and fixed to 1.       |                                           | Experimental. Attempts to improve on the old one.  |
| "StripedExecutorServiceMonitorNotificationServiceImpl {,threads}"              | Integer.MAX_VALUE       | 10                                             | Uses Heinz Kabbutz StripedExecutorService.         |

Note: 

1. The configuration of the monitor notification engine was always intended as an experimental feature. The LMAX-Disruptor
based notification engines which were available previously have now been retired. The primary reason for this is because 
they did not scale well to multiple channels. 
1. The ```BlockingQueueMultipleWorkerMonitorNotificationServiceImpl``` and ```StripedExecutorServiceMonitorNotificationServiceImpl```
notification engines provide optional configuration parameters allowing the size of the notification buffer and number 
of consumer notification threads to be configured. 
1. In the future it is likely that the ```StripedExecutorServiceMonitorNotificationServiceImpl``` will also be retired 
and that the __ca__ library will offer only a single notification engine based on the blocking queue implementation.  
This engine will remain fully configurable to meet the needs of all client applications.   
1. Further details on the requirements for the monitor notification engine and its performance are available in the
   following [MONITOR_INFO.md](https://github.com/channelaccess/ca/blob/master/MONITOR_INFO.md) file.

## Context

In order to create channels a Context must first be created. The context acts as a channel container. When the context 
is closed also all channels created with that context will be automatically closed.

This is how to create a context:

```
Context context = new Context()
```

Each context supports the configuration properties defined in the previous section. By default whencreated these 
properties are taken from the CA library environment. If finer-grained control is required over each context then
an appropriately configured properties object can be supplied in the class constructor.

```
Properties properties = new Properties();
properties.setProperty( Context.ProtocolConfiguration.EPICS_CA_ADDR_LIST.toString(), "10.10.10.255");
new Context(properties);
```

The context's resources (= network sockets and allocated memory) should be disposed of when the context is no longer 
required.  This can be achieved by callling the `close` method.

```
context.close();
```

Alternatively as the Context implements the Java `AutoCloseable` interface it can also be used inside a 
try-with-resources statement to ensure that the context's resources are automatically disposed when the 
context is no longer in scope. 

```
try ( Context context = new Context)
{
  // Code using context
}
```

## Channel
To create a channel use:

```
Channel<Double> channel = context.createChannel("MY_CHANNEL", Double.class);
```

At creation time of the channel its type need to be defined. If you want to have a generic type of the channel (i.e. you want to use the type set on the server) use:

```
Channel<Object> channel = context.createChannel("ARIDI-PCT:CURRENT", Object.class);
```

When getting a value from the channel you will get the correct/corresponding Java type that maps to the type set on the server.

After creating the channel object the channel needs to be connected. There is a synchronous and asynchronous way to do so. The synchronous/blocking way is to call:

```
channel.connect()
```

The asynchronous way is to call:

```
`connectAsync()`
```

`connectAsync()` will return a CompletableFuture. To check whether the connect was successful call `.get()` on it. The synchronous way to connect will block until the channel can be connected. If you want to specify a timeout for a connect use the asynchronous connect as follows:

```
channel.connectAsync().get(1, java.util.concurrent.TimeUnit.SECONDS);
```

For connecting multiple channels in parallel use:

```
Channel<Integer> channel1 = context.createChannel("adc02", Integer.class);
Channel<String> channel2 = context.createChannel("adc03", String.class);

// Wait for all channels to be connected
CompletableFuture.allOf(channel1.connectAsync(), channel2.connectAsync()).get();
```

A timeout for the multiple connect is realized the same way as with the single `connectAsync()`.


### Get / Put
After creating a channel you are able to get and put values via the `get()` and `put(value)` methods.

To put a value in a fire and forget style use `putNoWait(value)`. This method will put the value change request on the network but does not wait for any kind of acknowledgement.

```
// Get value
double value = channel.get();
// Set value
channel.put(10.0);
// Set value (best effort style)
channel.putNoWait(10.0);
```

Beside the synchronous (i.e. blocking until the operation is done) versions of `get()` and `put(value)` there are also asynchronous calls. They are named `getAsync()` and `putAsync(value)`. Both functions immediately return with a CompletableFuture for the operation. The Future can be used to wait at any location in the application and to wait for the completion of the operation and to retrieve the final value of the channel.

Example asynchronous get:

```
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

```
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

```
channel.get(Timestamped.class)
```

Ca supports all metadata types Channel Access provides, namely `Timestamped`, `Alarm`, `Graphic` and `Control`.

|Metadata Type | Metadata                   |
|--------------|----------------------------|
| Timestamped  | seconds, nanos             |
| Alarm        | alarmStatus, alarmSeverity |
| Graphic      | alarmStatus, alarmSeverity, units, precision, upperDisplay, lowerDisplay, upperAlarm, lowerAlarm, upperWarning, lowerWarning |
| Control      | alarmStatus, alarmSeverity, units, precision, upperDisplay, lowerDisplay, upperAlarm, lowerAlarm, upperWarning, lowerWarning, upperControl, lowerControl |

### Monitors
If you want to monitor a channel you can attach a monitor to it like this:

```
Monitor<Double> monitor = channel.addValueMonitor(value -> System.out.println(value));
```

To close a monitor use:

```
monitor.close()
```

Again if you would like to obtain metadata from the monitor you can specify the type of metadata that you are 
interested in.

```
Monitor<Timestamped<Double>> monitor =
    channel.addMonitor(
        Timestamped.class,
            value -> { if (value != null) System.out.println(new Date(value.getMillis()) + " / " + value.getValue()); }
            );
```

### Listeners
A channel can have Access Right and Connection listeners. These two types of listeners are attached as follows.


```
Listener connectionListener = channel.addConnectionListener((channel, state) -> System.out.println(channel.getName() + " is connected? " + state));


Listener accessRightListener = channel.addAccessRightListener((channel, rights) -> System.out.println(channel.getName() + " is rights? " + rights));
```
To remove the listener(s), or use `try-catch-resources` (i.e. Listeners implement `AutoCloseable`) or

```
listener.close()
```

_Note:_ These listeners can be attached to the channel before connecting.


### ConnectionState
The channels connection state can be checked as follows:

```
channel.getConnectionState()
```

## Channels
The utility class `Channels` provides various convenience functions to perform bulk operations on groups of channels. 

### Create
To create channels `Channels` provides these functions:

```
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

```
waitForValue(channel, "value")

// Use custom comparator for checking what is equal ...
Comparator<String> comparator = ...
waitForValue(channel, "value", comparator)
```

Both functions are also available in an __async__ version. Instead of blocking they return a CompletableFuture.

```
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
```
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

```
AnnotatedClass object = new AnnotatedClass();
Channels.create(context, object);
```

To close all annotated channels use:

```
Channels.close(object);
```

As channel names should not be hardcoded within an annotation, the name of a channel may contain multiple macros (e.g. `@CaChannel(name="adc${MACRO1}", type=String.class)`). While creating the channels a map of macros need to be passed to the `Channels.create` function.

```
Map<String,String> macros = new HashMap<>();
macros.put("MACRO1","01");
AnnotatedClass object = new AnnotatedClass();
Channels.create(context, object, macros);
```

Macro names are __case sensitive__!

## Examples

Create simple channel:

```
try (Context context = new Context())
{
  try(Channel<Double> channel = Channels.create(context, "MY_CHANNEL", Double.class)){
    System.out.println(channel.get());
  }
}
```

An extended usage example can be found at [src/main/java/org/epics/ca/examples/Example.java](src/main/java/org/epics/ca/examples/Example.java).

# Library API Documentation

Since __ca__ release 1.3.x the Javadoc for the library is published in the GitHub repository [pages](https://channelaccess.github.io/ca/) area.
 
_Note:_ the javadoc is currently in a rather rudimentary state. Over time we plan to improve it. 

# Developer / Maintainer Information

Please see the separate [DEVELOPER](DEVELOPER.md) notes.

# Contact

If you have questions please contact: 'simon.rees@psi.ch' or 'simon.ebner@psi.ch'.
