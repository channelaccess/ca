# Overview
The __ca__ package provides an easy to use Channel Access library for Matlab. It is cross platform and runs on all major operating systems - Linux, Windows, Mac OS X.

The *prerequisites* for this package is *Matlab2019a* or later. There are no other dependencies (just include the Jar as described below).

The latest release of this package can be downloaded [here](https://github.com/channelaccess/ca/releases).

After this download and copy the jar into your Matlab workspace and you are ready to go:

```Matlab
javaaddpath('ca-all-1.2.2.jar')
import org.epics.ca.*

context = Context();
channel = Channels.create(context, 'S10CB01-RBOC-DCP10:FOR-AMPLT-MAX');

channel.get()


% Get metadata for channels as described in https://github.com/channelaccess/ca#metadata
value = channel.get(org.epics.ca.data.Graphic().getClass())
value.getUnits()

channel.close()
context.close()
```

For more details on the available functions please below or refer to the main [Readme.md](Readme.md) of the library.


# Configuration

As **ca** requires Java 8 or later please make sure that your Matlab version is running a Java 8 or higher (this is the case starting with Matlab2019a) or that you point your Matlab to a suitable Java version. This can be done e.g. by setting the `MATLAB_JAVA` environment variable before starting Matlab from the Command Line

```bash
export MATLAB_JAVA=/opt/gfa/java/latest/jre
```

There are two options to use the library, a dynamic and a static one. With the dynamic option you will *bundle* the library with your Matlab code whereas with the static option you will set the library one time globally for your Matlab instance.

__We strongly suggest to use the dynamic approach due to following reasons:__

- Self-contained applications - Your applications can be easily executed on any machine that runs Matlab
(e.g. just git clone and execute). You are not dependent on any kind of pre-configuration,  infrastructure (e.g. Network shares, etc.)
- Upgrade at your own speed - Once a new version of the library is out you can upgrade to it at your own speed. There is also not the case that a centrally deployed library gets updated and your application starts behaving differently. 

Adding the library, disk space wise is not a big deal, its just around 600k (and will be even smaller in future)


## Dynamic

To get started with the library:
* Copy the downloaded jar into the folder holding your Matlab code file.
* At the top of your .m file add following line (remember to change the version to the actual one).

```matlab
javaaddpath('ca-1.2.2-all.jar')
```

In scripts that get executed several times in the same workspace:
* Use following construct to get rid of the Matlab warnings regarding re-adding to the classpath.

```matlab
if not(exist('java_classpath_set'))
    javaaddpath('ca-1.2.2-all.jar')
    java_classpath_set = 1;
end
```

In more complex Matlab projects you might want to have the library in a sub/parent-folder. In this case use relative paths to refer to the library.

```matlab
javaaddpath('../ca-1.2.2-all.jar')
```

For Windows users, keep in mind to use backslashes ( __\__ ) !


## Static
Include the _full qualified path_ of the jar in the *javaclasspath.txt* within the Matlab home folder (ideally also copy the jar into this directory). For example:

```
/some/location/MATLAB/ca-1.2.2-all.jar
```

After creating/altering the file *javaclasspath.txt* you need to restart Matlab.



# Usage

## Quick Start

After loading the required jar, channels can be created and be read/written as follows:

```Matlab
import org.epics.ca.*
context = Context();
channel = Channels.create(context, 'ARIDI-PCT:CURRENT');
channel.get()
channel.close();
context.close();
```

## Context
A context is necessary to create channels. Ideally, there should be one context per Matlab application only.

```Matlab
import org.epics.ca.*
context = Context();
```

It is also possible to configure the context via properties (i.e. to set the EPICS_CA_ADDR_LIST or EPICS_CA_MAX_ARRAY_BYTES).

```Matlab
import org.epics.ca.*
properties = java.util.Properties();
properties.setProperty('EPICS_CA_ADDR_LIST', '10.0.0.255');

context = Context(properties);
```

Currently following properties are supported:

|Name|Description|
|----|----|
|EPICS_CA_ADDR_LIST|Address list to search channel on|
|EPICS_CA_AUTO_ADDR_LIST|Automatically create address list|
|EPICS_CA_SERVER_PORT|Port of the channel access server|
|EPICS_CA_MAX_ARRAY_BYTES|Maximum number of bytes for an array/waveform|


To set e.g. the EPICS_CA_ADDR_LIST to the same value as the environment variable of the machine running the script you can use:

```matlab
properties.setProperty('EPICS_CA_ADDR_LIST', getenv('EPICS_CA_ADDR_LIST'));
```


The context needs to be closed at the end of the application via

```Matlab
context.close();
```

## Channel
To create a channel use the create function of the Channels utility class. The function's argument is either simply the channel name or a so called ChannelDescriptor which describes the desired channel, i.e. name, type, monitored (whether the channel object should be constantly monitoring the channel) as well as size (in case of array).

Here are some examples on how to create channels:

```Matlab
% Easiest and preferred way
channel = Channels.create(context, 'ARIDI-PCT:CURRENT');

% Explicitly define type
channel = Channels.create(context, ChannelDescriptor('ARIDI-PCT:CURRENT', java.lang.Double(0).getClass()));

% Create monitored double channel
channel = Channels.create(context, ChannelDescriptor('ARIDI-PCT:CURRENT', java.lang.Double(0).getClass(), true));
```

After creating a channel you are able to get and put values via the `get()` and `put(value)` methods.

__Note__: If you created a channel with the monitored flag set to *true*, `get()` does not access the network to get the latest value of the channel but returns the latest update by a channel monitor.
If you require to explicitly fetch the value over the network use `get(true)` (this should be rarely used as most of the time its enough to get the cached value)

__Note__: A polling loop within your Matlab application on a channel created with the monitored flag set to *true* is perfectly fine as it does not induce any load on the network.

To put a value in a fire and forget style use `putNoWait(value)`. This method will put the change request on the network but does not wait for any kind of acknowledgement.

```Matlab
value = channel.get();
channel.put(10.0);
channel.putNoWait(10.0);
```

Beside the synchronous (i.e. blocking until the operation is done) versions of `get()` and `put(value)` there are also asynchronous calls. They are named `getAsync()` and `putAsync(value)`. Both functions immediately return with a handle for the operation, i.e. a so called Future. This Future can be used to wait at any location in the script for the completion of the operation and retrieve the final value of the channel.

Example asynchronous get:

```Matlab
future = channel.getAsync();
future_2 = channel_2.getAsync();

% do something different ...
do_something();
% ... or simply sleep ...
pause(10);
% ... or simply do nothing ...

value_channel = future.get();
value_channel_2 = future_2.get();
```

Example asynchronous put:

```Matlab
future = channel.putAsync(value_1); % this could, for example start some move of a motor ...
future_2 = channel_2.putAsync(value_2);

% do something different ...
do_something();
% ... or simply sleep ...
pause(10);
% ... or simply do nothing ...

future.get();
future_2.get();
```

To specify a timeout for the get/put operation following code can be used:

```matlab
f = channel.getAsync();
f.get(10, java.util.concurrent.TimeUnit.SECONDS);
f = channel.putAsync(value);
f.get(10, java.util.concurrent.TimeUnit.SECONDS);
```

In both examples the operation would time out after 10 seconds. The function returns a `TimeoutException` if the operation times out.


Waiting for channels to reach a certain value can be done as follows:

```matlab
// Wait without timeout (i.e. forever)
Channels.waitForValue(channel, 'world');

// Wait with timeout
f = Channels.waitForValueAsync(channel, 'world');
f.get(10, java.util.concurrent.TimeUnit.SECONDS);
```

If you want to do stuff while waiting you can implement a busy loop like this:

```matlab
future = Channels.waitForValueAsync(channel, 'world');
while not(future.isDone())
    % do something
end
```


After you are done working with a channel close the channel via

```Matlab
channel.close();
```

# Examples
Examples can be found in the [examples](examples) folder within this repository.

# Issues / Feedback
We very much appreciate your feedback! Please drop an [issue](../../issues) for any bug or improvement you see for this library!

## Requesting Support
In case of problems with your application:
* Upgrade to latest version
* Check whether the problem persists
* If yes, open an issue
  * Provide code snippet causing the problem
  * Provide the library version used!


