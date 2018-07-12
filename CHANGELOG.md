# Overview

This log describes the functionality of tagged versions within the repository.

# Tags  
* [1.1.0-RELEASE](https://github.com/channelaccess/ca/releases/tag/1.1.0)
  ###### Overview:
  - This release provides an initial fix for the scalability bottleneck which prevented the use of more than ~4000 monitors
    in the Disruptor implementation. 
  - Additionally, various cleanups have been made with the intention of making this product more maintainable in the future.
  ###### Change List: 
  - [Issue #23](https://github.com/channelaccess/ca/issues/23) Documentation Improvements.
     - added comment to README file on CA version compatibility. 
     - added PDF of CA Protocol Specification. Source is [here]()https://epics.anl.gov/base/R3-16/1-docs/CAproto/index.html). 
     - added Enterprise Architect Model which can be useful for analysing/documenting the behaviour of this library. 
     - added additional logging (but no functional differences).         
  - [Issue #24:](https://github.com/channelaccess/ca/issues/24)  Upgraded for compatibility with latest versions of Java, Gradle, Disruptor, JUnit etc
     - adjusted build system for Junit 5. 
     - added Mockito framework to support unit testing of TCPTransport.
  - [Issue #25](https://github.com/channelaccess/ca/issues/25)  Fixed bug in TCPTransport receive buffer processing. 
     - added Unit Test which fails on the implementation before the fix. 
  - [Issue #26](https://github.com/channelaccess/ca/issues/26)  Removed TAB character from source files.
  - [Issue #27](https://github.com/channelaccess/ca/issues/27)  Eliminated Javadoc warnings. 
  - [Issue #28](https://github.com/channelaccess/ca/issues/28)  Fix monitor scalability bottleneck.
     - provided support for configurable MonitorNotificationService. 
     - added some additional performance tests.
     - added new test to explore socket handling performance. 
     - removed Disruptor from monitor interface.
     - removed queue zize from monitor interface. 
  - [Issue #29](https://github.com/channelaccess/ca/issues/29) Create software release 1.1.0-RELEASE. 
     - fixed bug in unit test which could result in test raising NPE. 
     - added parameterized unit testing for monitors.    
  - [Issue #30](https://github.com/channelaccess/ca/issues/30) Upgrade to Junit5.
  - [Issue #31](https://github.com/channelaccess/ca/issues/31) Adapt project structure so that 'src/test/java' tree is congruent with 'src/main/java' tree.
  - [Issue #32](https://github.com/channelaccess/ca/issues/32) Add support for uploading builds to PSI artifactory.
  - [Issue #33](https://github.com/channelaccess/ca/issues/33) Cleanup warnings detected by IntelliJ.
  - [Issue #34](https://github.com/channelaccess/ca/issues/34) Provide direct access to socket ByteBuffer through MonitorNotifcationService interface.
  - [Issue #35](https://github.com/channelaccess/ca/issues/35) Revert Java buildCompatibility target to Java 8.
  - [Issue #36](https://github.com/channelaccess/ca/issues/35) Revert to java.util.logging throughout.