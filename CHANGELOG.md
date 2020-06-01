# Overview

This log describes the functionality of tagged versions within the repository.

# Tags  
* [1.1.0-RELEASE](https://github.com/channelaccess/ca/releases/tag/1.1.0) Released 2018-07-12
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
  
  
* [1.2.0](https://github.com/channelaccess/ca/releases/tag/1.2.0) Released 2018-07-30
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
   
* [1.2.1](https://github.com/channelaccess/ca/releases/tag/1.2.1) Released 2018-09-18
   ###### Overview:
   As previous release, except now fixed deployment for Java 8 target.
   ###### Change List:    
   * [Issue #45](https://github.com/channelaccess/ca/issues/45) Reverted to building for Java 8 target.
   * [Issue #46](https://github.com/channelaccess/ca/issues/46) Create software release 1.2.1. 
   * [Issue #47](https://github.com/channelaccess/ca/issues/47) Add initial support for deploying via bintray/jcenter.
   
* [1.2.2](https://github.com/channelaccess/ca/releases/tag/1.2.2) Released 2019-10-09
   ###### Overview:
   Bug fix release to handle connection problems when operating through PSI's channel access gateway.
   ###### Change List:    
   * [Issue #48](https://github.com/channelaccess/ca/issues/48) Create software release 1.2.2. 
   * [Issue #49](https://github.com/channelaccess/ca/issues/49) Library fails to detect IOC connection state changes when operating through a firewall.
   * [Issue #50](https://github.com/channelaccess/ca/issues/50) Switched to openjdk8. 
   
* [1.3.0](https://github.com/channelaccess/ca/releases/tag/1.3.0) Released 2020-05-08.
  ###### Overview:
  Release was originally triggered mainly by need to address problems when interoperating with EPICS 7 Channel Access (see Issue #58). 
  But other changes to modernise build chain to use later tools.
  Also refactored lots of code following IntelliJ code inspection recommendations.
  Also CA Repeater was refactored for improved clarity resulting in the creation of many new unit tests to verify the behaviour.
  Also: thanks to JL Muir for his contributions. :-)

  ###### Change List:   
  * [Issue #52](https://github.com/channelaccess/ca/issues/52) Fix readme Gradle fragment version. Credit JL Muir. :-)
  * [Issue #53](https://github.com/channelaccess/ca/issues/53) README unclear about supported listeners. Credit JL Muir. :-)
  * [Issue #54](https://github.com/channelaccess/ca/issues/54) Fix readme typo in Compatibility section. Credit JL Muir. :-)
  * [Issue #55](https://github.com/channelaccess/ca/issues/55) Remove nonexistent Travis build status from readme. Credit JL Muir. :-)
  * [Issue #56](https://github.com/channelaccess/ca/issues/56) Fix readme typos. Credit JL Muir. :-)
  * [Issue #57](https://github.com/channelaccess/ca/issues/57) Improve robustness of some tests.
  * [Issue #58](https://github.com/channelaccess/ca/issues/58) Library does not interoperate with CA in EPICS 7.0 - "client version too old".
  * [Issue #59](https://github.com/channelaccess/ca/issues/59) Transition away from 'travis-ci.org' site to newer 'travis-ci.com' site.
  * [Issue #60](https://github.com/channelaccess/ca/issues/60) Create software release 1.2.3. Note: this was never publicly released.
  * [Issue #61](https://github.com/channelaccess/ca/issues/61) Get the Example Program Working. 
  * [Issue #62](https://github.com/channelaccess/ca/issues/62) Update CA test server to use more recent JCA release
  * [Issue #63](https://github.com/channelaccess/ca/issues/63) Update dependencies to later versions.
  * [Issue #64](https://github.com/channelaccess/ca/issues/64) Refactor project according to IntelliJ inspection suggestions.
  * [Issue #65](https://github.com/channelaccess/ca/issues/65) Change name of CAJTestServer to EpicsChannelAccesstestServer.
  * [Issue #66](https://github.com/channelaccess/ca/issues/66) Update gradle to more recent build paradigms.
  * [Issue #67](https://github.com/channelaccess/ca/issues/67) Switch tests to use of hamcrest matchers.
  * [Issue #68](https://github.com/channelaccess/ca/issues/68) Modernise build.
  * [Issue #69](https://github.com/channelaccess/ca/issues/69) Update .gitignore file using output from generator tool.
  * [Issue #70](https://github.com/channelaccess/ca/issues/70) Refactor CA Repeater for improved clarity.
  * [Issue #71](https://github.com/channelaccess/ca/issues/71) Refactor Version integration for better clarity.
  * [Issue #72](https://github.com/channelaccess/ca/issues/72) Add support for running tests from command-line when deployed on target platform.
  * [Issue #73](https://github.com/channelaccess/ca/issues/73) Create Tool for sniffing UDP datagram packets.
  * [Issue #74](https://github.com/channelaccess/ca/issues/74) Cleanup code formatting (no change of behaviour).
  * [Issue #75](https://github.com/channelaccess/ca/issues/75) Add constant for CA Repeater Registration Interval (60 seconds).
  * [Issue #76](https://github.com/channelaccess/ca/issues/76) Implement IntelliJ code improvement suggestions.
  * [Issue #77](https://github.com/channelaccess/ca/issues/77) Change README.xx files to upper case (for better consistency with other repos).
  * [Issue #78](https://github.com/channelaccess/ca/issues/78) Update Channel Access Test Server to use latest from JCA project.
  * [Issue #79](https://github.com/channelaccess/ca/issues/79) Miscellaneous improvements to unit tests.
  * [Issue #80](https://github.com/channelaccess/ca/issues/80) Update CHANGELOG with details of software release 1.3.0.
  * [Issue #81](https://github.com/channelaccess/ca/issues/81) Refactor / simplify logging facilities.
  * [Issue #82](https://github.com/channelaccess/ca/issues/82) Restructure using standard class template.
  * [Issue #83](https://github.com/channelaccess/ca/issues/83) Further improvements to Example program.
  * [Issue #84](https://github.com/channelaccess/ca/issues/84) Further test improvements
  * [Issue #85](https://github.com/channelaccess/ca/issues/85) Modify Example program to start/stop EPICS Test Server automatically.
  * [Issue #86](https://github.com/channelaccess/ca/issues/86) Create markdown file for developer notes.
  * [Issue #87](https://github.com/channelaccess/ca/issues/87) Add various run targets to gradle build script.
  * [Issue #88](https://github.com/channelaccess/ca/issues/88) Refactor general-purpose network related functions into separate class.
  * [Issue #89](https://github.com/channelaccess/ca/issues/89) Start repeater registration process immediately after CA Repeater

  ###### Test Results:   
  * MacOsX:
  * Linux RHEL7:
  * Windows 10: