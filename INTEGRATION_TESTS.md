# Performance Testing

This document presents the results of integration testing on the current and previous release. By comparing the 
results it is hoped we may spot perfromance regressions that might otherwise go unnoticed.

Before running the tests an EPICS SoftIOC should be started based on the [epics_tests.db](src/integrationTest/resources/epics_tests.db).

```
export EPICS_CA_MAX_ARRAY_BYTES=1000000000
softIoc -d epics_tests.db 
```

## Testing on Software Release CA-1.3.2-RC1

 * Date: 2020-06-26
 * EPICS Base Version used for SoftIOC: 7.0.4.1-DEV
 * IOC Test Platform: MacBook Pro 2018 vintage.  
 * CA Library Test Platform: MacBook Pro 2018 vintage.  
 
### CA Context Tests

Q1: Can the context manual close feature be relied on to cleanup the created channels ? Answer: **YES**.

Q2: Can the context autoclose feature be relied on to cleanup the created channels ? Answer: **YES**.

Q3: How many contexts can be created ? Answer: **at least 150**.  

Q4: What is the context creation cost ? Answer: **See below.**  
```  
- Creating 1 contexts took 1517 ms. Average: 1517.000 ms  
- Creating 10 contexts took 1579 ms. Average: 157.900 ms  
- Creating 50 contexts took 2013 ms. Average: 40.260 ms  
- Creating 100 contexts took 3565 ms. Average: 35.650 ms  
- Creating 150 contexts took 5790 ms. Average: 38.600 ms  
```  
Q5: Do all contexts share the same returned object ? Answer: **NO**.  
Context object names were as follows:  
```  
- Context object 1 had name: org.epics.ca.Context@2f35201b  
- Context object 10 had name: org.epics.ca.Context@41bac4f5  
- Context object 50 had name: org.epics.ca.Context@7709e2ca  
- Context object 100 had name: org.epics.ca.Context@2ec438b  
- Context object 150 had name: org.epics.ca.Context@45f9e35  
```  

### CA Channel Tests

Q10: How many channels can be created ? Answer: **at least 400000**.

Q11: What is the channel creation cost ? Answer: **See below.**
```
- Creating 1 channels took 0 ms. Average: 0.000 ms  
- Creating 10 channels took 0 ms. Average: 0.000 ms  
- Creating 100 channels took 1 ms. Average: 0.010 ms  
- Creating 1000 channels took 8 ms. Average: 0.008 ms  
- Creating 10000 channels took 26 ms. Average: 0.003 ms  
- Creating 50000 channels took 94 ms. Average: 0.002 ms  
- Creating 100000 channels took 194 ms. Average: 0.002 ms  
- Creating 200000 channels took 336 ms. Average: 0.002 ms  
- Creating 400000 channels took 2606 ms. Average: 0.007 ms  

Q12: Do all channels connected to the same PV share the same returned object ? Answer: **NO**.

Channel object names were as follows:
```
- Channel object 1 had name: 'org.epics.ca.impl.ChannelImpl@57607f7a'  
- Channel object 10 had name: 'org.epics.ca.impl.ChannelImpl@28e6de71'  
- Channel object 100 had name: 'org.epics.ca.impl.ChannelImpl@6e47d077'  
- Channel object 1000 had name: 'org.epics.ca.impl.ChannelImpl@51726cae'  
- Channel object 10000 had name: 'org.epics.ca.impl.ChannelImpl@53d4255'  
- Channel object 50000 had name: 'org.epics.ca.impl.ChannelImpl@1b3601d5'  
- Channel object 100000 had name: 'org.epics.ca.impl.ChannelImpl@4ba2aeac'  
- Channel object 200000 had name: 'org.epics.ca.impl.ChannelImpl@9f7ac6a'  
- Channel object 400000 had name: 'org.epics.ca.impl.ChannelImpl@663768eb'  
```
Q13: How many connected channels can the library simultaneously support ? Answer: **at least 2000**.

Q14: What is the cost of synchronously connecting channels (using Channel class) ? Answer: **See below.**
```
- Synchronously connecting 1 channels took 9 ms. Average: 9.000 ms  
- Synchronously connecting 10 channels took 61 ms. Average: 6.100 ms  
- Synchronously connecting 100 channels took 1035 ms. Average: 10.350 ms  
- Synchronously connecting 500 channels took 4855 ms. Average: 9.710 ms  
- Synchronously connecting 1000 channels took 9386 ms. Average: 9.386 ms  
- Synchronously connecting 2000 channels took 18410 ms. Average: 9.205 ms  
```
Q15: What is the cost of creating channels which will asynchronously connect ? Answer: **See below.**
```
- Creating 1 channels with asynchronous connect policy took 4 ms. Average: 4.000 ms  
- Creating 10 channels with asynchronous connect policy took 4 ms. Average: 0.400 ms  
- Creating 100 channels with asynchronous connect policy took 5 ms. Average: 0.050 ms  
- Creating 1000 channels with asynchronous connect policy took 10 ms. Average: 0.010 ms  
- Creating 10000 channels with asynchronous connect policy took 33 ms. Average: 0.003 ms  
- Creating 50000 channels with asynchronous connect policy took 131 ms. Average: 0.003 ms  
- Creating 100000 channels with asynchronous connect policy took 240 ms. Average: 0.002 ms  
- Creating 150000 channels with asynchronous connect policy took 364 ms. Average: 0.002 ms  
- Creating 200000 channels with asynchronous connect policy took 477 ms. Average: 0.002 ms  
```
Q16: How long does it take for channels to connect asynchronously ? Answer: **See below.**
```
- Connecting 1 channels asynchronously took 10 ms. Average: 10.000 ms.  
- Connecting 10 channels asynchronously took 15 ms. Average: 1.500 ms.  
- Connecting 100 channels asynchronously took 17 ms. Average: 0.170 ms.  
- Connecting 1000 channels asynchronously took 123 ms. Average: 0.123 ms.  
- Connecting 10000 channels asynchronously took 1964 ms. Average: 0.196 ms.  
- Connecting 50000 channels asynchronously took 6251 ms. Average: 0.125 ms.  
- Connecting 100000 channels asynchronously took 11675 ms. Average: 0.117 ms.  
- Connecting 150000 channels asynchronously took 17165 ms. Average: 0.114 ms.  
- Connecting 200000 channels asynchronously took 22634 ms. Average: 0.113 ms.  
```
Q17: What is the cost of performing a synchronous get on multiple channels (same PV) ? Answer: **See below.**
```
- Synchronous Get from 1 channels took 5 ms. Average: 5.000 ms  
- Synchronous Get from 10 channels took 7 ms. Average: 0.700 ms  
- Synchronous Get from 100 channels took 31 ms. Average: 0.310 ms  
- Synchronous Get from 1000 channels took 100 ms. Average: 0.100 ms  
- Synchronous Get from 10000 channels took 715 ms. Average: 0.072 ms  
- Synchronous Get from 20000 channels took 1231 ms. Average: 0.062 ms  
- Synchronous Get from 40000 channels took 2247 ms. Average: 0.056 ms  
- Synchronous Get from 60000 channels took 3340 ms. Average: 0.056 ms  
- Synchronous Get from 80000 channels took 4342 ms. Average: 0.054 ms  
```
Q18: What is the cost of performing an asynchronous get on multiple channels (same PV) ? Answer: **See below.**
```
- Asynchronous Get from 1 channels took 2 ms. Average: 2.000 ms  
- Asynchronous Get from 10 channels took 4 ms. Average: 0.400 ms  
- Asynchronous Get from 100 channels took 6 ms. Average: 0.060 ms  
- Asynchronous Get from 1000 channels took 21 ms. Average: 0.021 ms  
- Asynchronous Get from 10000 channels took 113 ms. Average: 0.011 ms  
- Asynchronous Get from 20000 channels took 209 ms. Average: 0.010 ms  
- Asynchronous Get from 40000 channels took 361 ms. Average: 0.009 ms  
- Asynchronous Get from 60000 channels took 491 ms. Average: 0.008 ms  
- Asynchronous Get from 80000 channels took 633 ms. Average: 0.008 ms  
- Asynchronous Get from 100000 channels took 766 ms. Average: 0.008 ms  
```
Q19: What is the cost of performing an asynchronous get on multiple channels (different PVs) ? Answer: **See below.**
```
- Asynchronous Get from 1 channels took 1 ms. Average: 1.000 ms  
- Asynchronous Get from 10 channels took 2 ms. Average: 0.200 ms  
- Asynchronous Get from 100 channels took 11 ms. Average: 0.110 ms  
- Asynchronous Get from 1000 channels took 14 ms. Average: 0.014 ms  
- Asynchronous Get from 10000 channels took 98 ms. Average: 0.010 ms  
- Asynchronous Get from 20000 channels took 154 ms. Average: 0.008 ms  
- Asynchronous Get from 40000 channels took 276 ms. Average: 0.007 ms  
- Asynchronous Get from 60000 channels took 386 ms. Average: 0.006 ms  
- Asynchronous Get from 80000 channels took 508 ms. Average: 0.006 ms  
- Asynchronous Get from 100000 channels took 632 ms. Average: 0.006 ms  
```
Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**
```
Implementation: BlockingQueueSingleWorkerMonitorNotificationServiceImpl
- Asynchronous Monitor from 1 channels took 12 ms. Average: 12.000 ms  
- Asynchronous Monitor from 100 channels took 21 ms. Average: 0.210 ms  
- Asynchronous Monitor from 200 channels took 30 ms. Average: 0.150 ms  
- Asynchronous Monitor from 500 channels took 42 ms. Average: 0.084 ms  
- Asynchronous Monitor from 1000 channels took 53 ms. Average: 0.053 ms  
```
Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**
```
Implementation: BlockingQueueMultipleWorkerMonitorNotificationServiceImpl
- Asynchronous Monitor from 1 channels took 2 ms. Average: 2.000 ms  
- Asynchronous Monitor from 100 channels took 5 ms. Average: 0.050 ms  
- Asynchronous Monitor from 200 channels took 8 ms. Average: 0.040 ms  
- Asynchronous Monitor from 500 channels took 17 ms. Average: 0.034 ms  
- Asynchronous Monitor from 1000 channels took 20 ms. Average: 0.020 ms
```
Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**
```
Implementation: StripedExecutorServiceMonitorNotificationServiceImpl
- Asynchronous Monitor from 1 channels took 9 ms. Average: 9.000 ms  
- Asynchronous Monitor from 100 channels took 19 ms. Average: 0.190 ms  
- Asynchronous Monitor from 200 channels took 20 ms. Average: 0.100 ms  
- Asynchronous Monitor from 500 channels took 26 ms. Average: 0.052 ms  
- Asynchronous Monitor from 1000 channels took 37 ms. Average: 0.037 ms  
```
Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**
```
Implementation: BlockingQueueSingleWorkerMonitorNotificationServiceImpl
- Transfer time for integer array of 10000 elements took 1 ms. Transfer rate: 38.147 MB/s  
- Transfer time for integer array of 20000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 50000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 100000 elements took 1 ms. Transfer rate: 381.470 MB/s  
- Transfer time for integer array of 200000 elements took 1 ms. Transfer rate: 762.939 MB/s  
- Transfer time for integer array of 500000 elements took 6 ms. Transfer rate: 317.891 MB/s  
- Transfer time for integer array of 1000000 elements took 5 ms. Transfer rate: 762.939 MB/s  
- Transfer time for integer array of 2000000 elements took 13 ms. Transfer rate: 586.877 MB/s  
- Transfer time for integer array of 5000000 elements took 40 ms. Transfer rate: 476.837 MB/s  
- Transfer time for integer array of 10000000 elements took 79 ms. Transfer rate: 482.873 MB/s  
```
Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**
```
Implementation: BlockingQueueMultipleWorkerMonitorNotificationServiceImpl
- Transfer time for integer array of 10000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 20000 elements took 2 ms. Transfer rate: 38.147 MB/s  
- Transfer time for integer array of 50000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 100000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 200000 elements took 1 ms. Transfer rate: 762.939 MB/s  
- Transfer time for integer array of 500000 elements took 6 ms. Transfer rate: 317.891 MB/s  
- Transfer time for integer array of 1000000 elements took 5 ms. Transfer rate: 762.939 MB/s  
- Transfer time for integer array of 2000000 elements took 13 ms. Transfer rate: 586.877 MB/s  
- Transfer time for integer array of 5000000 elements took 28 ms. Transfer rate: 681.196 MB/s  
- Transfer time for integer array of 10000000 elements took 77 ms. Transfer rate: 495.415 MB/s  
```
Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**
```
Implementation: StripedExecutorServiceMonitorNotificationServiceImpl
- Transfer time for integer array of 10000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 20000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 50000 elements took 3 ms. Transfer rate: 63.578 MB/s  
- Transfer time for integer array of 100000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 200000 elements took 1 ms. Transfer rate: 762.939 MB/s  
- Transfer time for integer array of 500000 elements took 6 ms. Transfer rate: 317.891 MB/s  
- Transfer time for integer array of 1000000 elements took 7 ms. Transfer rate: 544.957 MB/s  
- Transfer time for integer array of 2000000 elements took 9 ms. Transfer rate: 847.711 MB/s  
- Transfer time for integer array of 5000000 elements took 37 ms. Transfer rate: 515.500 MB/s  
- Transfer time for integer array of 10000000 elements took 88 ms. Transfer rate: 433.488 MB/s  
```

### CA Channels Tests

Q31: What is the cost of synchronously connecting channels (using Channels class) ? Answer: **See below.**
```
- Synchronously connecting 1 channels took 11 ms. Average: 11.000 ms  
- Synchronously connecting 10 channels took 52 ms. Average: 5.200 ms  
- Synchronously connecting 100 channels took 1028 ms. Average: 10.280 ms  
- Synchronously connecting 500 channels took 4816 ms. Average: 9.632 ms  
- Synchronously connecting 1000 channels took 9465 ms. Average: 9.465 ms  
```
