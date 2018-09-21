# Overview

This log describes the functionality of tagged versions within the repository.

# Tags  
* [1.1.0-RELEASE](https://github.com/channelaccess/ca/releases/tag/1.1.0)
  ###### Overview:
  - This release provides an initial fix for the scalability bottleneck which prevented the use of more than ~4000 monitors
    in the Disruptor implementation. The default monitor implementation now uses a single shared work queue from which ten 
    threads take off tasks. The chosen implementation is configurable via a property ('CA_MONITOR_NOTIFIER') which may be 
    set differently for each context or globally. The following values are supported:
      - MultipleWorkerBlockingQueueMonitorNotificationServiceImpl  <<< DEFAULT
      - SingleWorkerBlockingQueueMonitorNotificationServiceImpl
      - DisruptorMonitorNotificationServiceOldImpl"
      - DisruptorMonitorNotificationServiceNewImpl
  - Additionally, various cleanups have been made with the intention of making this library more maintainable in the future.
  ###### Change List: 
  - [Issue #23](https://github.com/channelaccess/ca/issues/23) Documentation Improvements.
     - added comment to README file on CA version compatibility. 
     - added PDF of CA Protocol Specification. Source is [here](https://epics.anl.gov/base/R3-16/1-docs/CAproto/index.html). 
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
  - [Issue #34](https://github.com/channelaccess/ca/issues/34) Provide direct access to socket ByteBuffer through MonitorNotificationService interface.
  - [Issue #35](https://github.com/channelaccess/ca/issues/35) Revert Java buildCompatibility target to Java 8.
  - [Issue #36](https://github.com/channelaccess/ca/issues/35) Revert to java.util.logging throughout.
  
  
* [1.2.0](https://github.com/channelaccess/ca/releases/tag/1.2.0)
   ###### Overview:
   * Provides configurability of the monitor -> consumer notification engine so that the user can choose between a number of implementations including,
      * BlockingQueueMultipleWorkerMonitorNotificationServiceImpl (default)
      * BlockingQueueSingleWorkerMonitorNotificationServiceImpl
      * DisruptorOldMonitorNotificationServiceImpl
      * DisruptorNewMonitorNotificationServiceImpl
      * StripedExecutorServiceMonitorNotificationServiceImpl
   * Changes the default monitor notification engine so that it buffers notification messages to slow consumers rather than throwing them away.
   * Now reports notification buffer overruns to stdout rather than silently throwing them away.
   * Fixes an issue which could occasionally occur with notification messages being delivered out of sequence.
   * Improves the monitor documentation available in the [README](https://github.com/channelaccess/ca/blob/master/README.md) file and in the [MONITOR_INFO](https://github.com/channelaccess/ca/blob/master/MONITOR_INFO.md) file.
   * Provides documentation on measured library performance. See the [MONITOR_INFO](https://github.com/channelaccess/ca/blob/master/MONITOR_INFO.md) file.
   * Increases the test coverage (includes more unit tests; switched to parameterized testing).
   ###### Change List:    
   - [Issue #37](https://github.com/channelaccess/ca/issues/37) Investigate and where necessary fix bug suggested by Fabian with respect to out of order notifications.
   - [Issue #39](https://github.com/channelaccess/ca/issues/39) Provide the ability for library users to configure the appropriate monitor notification strategies. 
   - [Issue #40](https://github.com/channelaccess/ca/issues/40) Decide on whether to keep possibility to send special monitor values (currently null) when a channel disconnects. 
   - [Issue #41](https://github.com/channelaccess/ca/issues/41) Write a monitor notification service impl that buffers to ensure that notifications are not dropped (eg suitable for archiver)
   - [Issue #42](https://github.com/channelaccess/ca/issues/42) Create software release 1.2.0. 
   - [Issue #43](https://github.com/channelaccess/ca/issues/43) Add support for StripedExecutorService. 
   
* [1.2.1](https://github.com/channelaccess/ca/releases/tag/1.2.1)
   ###### Overview:
   As previous release, except now fixed deployment for Java 8 target.
   ###### Change List:    
   * [Issue #45](https://github.com/channelaccess/ca/issues/45) Reverted to building for Java 8 target.
   * [Issue #46](https://github.com/channelaccess/ca/issues/46) Create software release 1.2.1. 
   * [Issue #47](https://github.com/channelaccess/ca/issues/47) Add initial support for deploying via bintray/jcenter.
