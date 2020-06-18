 # Developer Notes   
    
This file contains random notes intended to assist software developers.

## Java Network Configuration for CA Library
    
We need to force the library to use only IPv4 sockets since EPICS CA protocol does not work with IPv6 sockets.

see http://java.sun.com/j2se/1.5.0/docs/guide/net/properties.html

A more detailed summary of the Java network stack properties is available on the Red Hat pages here:
https://access.redhat.com/documentation/en-us/red_hat_jboss_web_server/3.1/html/installation_guide/java_ipv4_ipv6_properties
The text of that article is reproduced below.

### Configuring Java Properties

In Java there are 2 properties that are used to configure IPv4 and IPv6. These are java.net.preferIPv4Stack and 
java.net.preferIPv6Addresses.

#### java.net.preferIPv4Stack (default: false)


If IPv6 is available then the underlying native socket, by default, is an IPv6 socket. This socket lets applications 
connect and accept connections from IPv4 and IPv6 hosts. If application use only IPv4 sockets, then set this property 
to true. However, it will not be possible for the application to communicate with IPv6 only hosts.

#### java.net.preferIPv6Addresses (default: false)

If a host has both IPv4 and IPv6 addresses, and IPv6 is available, then the default behavior is to use IPv4 
addresses over IPv6. This allows backward compatibility. If applications that depend on an IPv4 address representation, 
for example: 192.168.1.1. Then, set this property to true to change the preference and use IPv6 addresses over IPv4 
where possible."


#

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
