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
