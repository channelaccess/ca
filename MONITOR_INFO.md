# Requirements for a Monitor -> Consumer Notification Engine

These are the requirements that were considered when designing the current implementation of the monitor notification service:

1. **A slow Consumer should not stop other _Consumers_ from eating**  
What does this mean ?  A fast update rate on one monitored channel should not result in other _Consumers_ getting starved of data. Obviously, the CPU is not an infinite resource so when excessive demands are made on the notification system it has only limited choices. The more concrete requirements should be taken as follows:
   - (a) a single slow Consumer should not block all other Consumers from being notified.
   - (b) CPU bandwidth should be _shared fairly_ by default.
   - (c) CPU bandwidth should be _shared appropriately_  when the library user has provided some additional usage hints.
1. The system should cater for both **_Lossy_** and **_Non-Lossy_ Consumers.** See Additional Notes.
1. If a _Lossy Consumer_ is too slow to eat then **it is acceptable to silently drop intermediate notification values**.   However:
   - (a) values must always be **sent in the order in which they were received** from the TCP/IP monitor update stream, and 
   - (b) after a stream of notifications the _Consumer_ must always finally be sent the last recently updated value.
1. If a _Non-Lossy Consumer_ is too slow to eat then **data must be buffered** until it can be delivered. Clearly there are limits to this, so the following additional rules apply:
   - (a) the buffer should be of **unlimited size** by default.
   - (b) the buffer can optionally be of **limited size** if the library user has provided some sizing hints.
1. When an attempt is made to publish information to a _Non-Lossy Consumer_ with a full buffer the **default policy will be to drop the oldest value.** This should not be done silently - the library return code should make it clear that information was lost.
1. _Consumers_ should be **sent values as closely as possible in the sequence that the values were received** from the TCP/IP monitor update stream. This keeps latency as low as possible.
1. It should be possible to monitor at least **100_000** channels without running into performance bottlenecks. Since most JVM installations have a resource limit of only a few thousand threads this means that it is impractical for implementations to dedicate a single thread per monitor.
1. Monitor notification comes from EPICS IOCs. With todays scan rates it is highly unlikely that the event rate from any IOC will exceed **1 event/ms**.  However, the CA client library should potentially be able to cope with data streaming from 100,000 PV's. This puts an estimate on the upper bound of required performance of 100,000 * 1000 * 24 bytes/second = **24GB/second**.  This exceeds the network bandwidth of Gigabit ethernet so for all practical purposes **the processing performance requirement is determined by the network bandwidth itself**.
1. The data associated with each IOC event may vary typically from the CA minimum header size (16 bytes) through the size associated with the transfer of simple data types (8 extra bytes) to the size associated with a camera image (several megabytes). It is believed that the transfer of camera data can get close to, or even exceed the Gigabit network bandwidth. This is probably somewhere around **120 MBytes/second**. See [here](http://rickardnobel.se/actual-throughput-on-gigabit-ethernet/) for a discussion on this.

#### Additional Notes:
1. _Lossy Consumer_ example: a GUI application in the WBGB Control Room.
1. _Non-Lossy Consumer_ example:  a Data Archiver application eg for SwissFEL.

_Note:_ Since the retirement of the LMAX Disruptor-based monitor notification engine implementations (in versions 
1.3.1 and later) the CA library no longer supports a lossy notification mechanismn. This feature may be reintroduced 
in a future release (albeit not using the Disruptor) should there be sufficient demand.
