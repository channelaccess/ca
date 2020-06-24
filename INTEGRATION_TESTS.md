# Performance Testing

This document presents the results of integration testing on the current and previous release. By comparing the 
results it is hoped we may spot perfromance regressions that might otherwise go unnoticed.

Before running the tests an EPICS SoftIOC should be started based on the [epics_tests.db](src/integrationTest/resources/epics_tests.db).

```
export EPICS_CA_MAX_ARRAY_BYTES=1000000000
softIoc -d epics_tests.db 
```

## Testing on Software Release CA-1.3.1

 * Date: 2020-06-24
 * EPICS Base Version used for SoftIOC: 3.14.7
 * Test Platform: MacBook Pro 2018 vintage.  
 
### CA Context Tests

Q1: Can the context manual close feature be relied on to cleanup the created channels ? Answer: **YES**.

Q2: Can the context autoclose feature be relied on to cleanup the created channels ? Answer: **YES**.

Q3: How many contexts can be created ? Answer: **at least 150**.  

Q4: What is the context creation cost ? Answer: **See below.**  
```  
- Creating 1 contexts took 14 ms. Average: 14.000 ms  
- Creating 10 contexts took 104 ms. Average: 10.400 ms  
- Creating 50 contexts took 982 ms. Average: 19.640 ms  
- Creating 100 contexts took 2587 ms. Average: 25.870 ms  
- Creating 150 contexts took 6568 ms. Average: 43.787 ms  
```  
Q5: Do all contexts share the same returned object ? Answer: **NO**.  
Context object names were as follows:  
```  
- Context object 1 had name: org.epics.ca.Context@335ec642  
- Context object 10 had name: org.epics.ca.Context@2e314cae  
- Context object 50 had name: org.epics.ca.Context@75e3bf57  
- Context object 100 had name: org.epics.ca.Context@19afbade  
- Context object 150 had name: org.epics.ca.Context@5c86137f  
```  

### CA Channel Tests

Q10: How many channels can be created ? Answer: **at least 400000**.

Q11: What is the channel creation cost ? Answer: **See below.**
```
- Creating 1 channels took 0 ms. Average: 0.000 ms  
- Creating 10 channels took 1 ms. Average: 0.100 ms  
- Creating 100 channels took 3 ms. Average: 0.030 ms  
- Creating 1000 channels took 16 ms. Average: 0.016 ms  
- Creating 10000 channels took 48 ms. Average: 0.005 ms  
- Creating 50000 channels took 194 ms. Average: 0.004 ms  
- Creating 100000 channels took 351 ms. Average: 0.004 ms  
- Creating 200000 channels took 501 ms. Average: 0.003 ms  
- Creating 400000 channels took 2390 ms. Average: 0.006 ms  
```
Q12: Do all channels connected to the same PV share the same returned object ? Answer: **NO**.

Channel object names were as follows:
```
- Channel object 1 had name: 'org.epics.ca.impl.ChannelImpl@6d98390d'  
- Channel object 10 had name: 'org.epics.ca.impl.ChannelImpl@5b46dd10'  
- Channel object 100 had name: 'org.epics.ca.impl.ChannelImpl@274ed297'  
- Channel object 1000 had name: 'org.epics.ca.impl.ChannelImpl@6130a81e'  
- Channel object 10000 had name: 'org.epics.ca.impl.ChannelImpl@4dcca564'  
- Channel object 50000 had name: 'org.epics.ca.impl.ChannelImpl@200f4f90'  
- Channel object 100000 had name: 'org.epics.ca.impl.ChannelImpl@7104eb6c'  
- Channel object 200000 had name: 'org.epics.ca.impl.ChannelImpl@1590cd0a'  
- Channel object 400000 had name: 'org.epics.ca.impl.ChannelImpl@3ddfbd59'  
```
Q13: How many connected channels can the library simultaneously support ? Answer: **at least 2000**.

Q14: What is the cost of synchronously connecting channels (using Channel class) ? Answer: **See below.**
```
- - Synchronously connecting 1 channels took 4 ms. Average: 4.000 ms  
- - Synchronously connecting 10 channels took 46 ms. Average: 4.600 ms  
- - Synchronously connecting 100 channels took 921 ms. Average: 9.210 ms  
- - Synchronously connecting 500 channels took 4805 ms. Average: 9.610 ms  
- - Synchronously connecting 1000 channels took 9049 ms. Average: 9.049 ms  
- - Synchronously connecting 2000 channels took 17283 ms. Average: 8.642 ms  
```
Q15: What is the cost of creating channels which will asynchronously connect ? Answer: **See below.**
```
- Creating 1 channels with asynchronous connect policy took 6 ms. Average: 6.000 ms  
- Creating 10 channels with asynchronous connect policy took 6 ms. Average: 0.600 ms  
- Creating 100 channels with asynchronous connect policy took 7 ms. Average: 0.070 ms  
- Creating 1000 channels with asynchronous connect policy took 10 ms. Average: 0.010 ms  
- Creating 10000 channels with asynchronous connect policy took 90 ms. Average: 0.009 ms  
- Creating 50000 channels with asynchronous connect policy took 266 ms. Average: 0.005 ms  
- Creating 100000 channels with asynchronous connect policy took 446 ms. Average: 0.004 ms  
- Creating 150000 channels with asynchronous connect policy took 612 ms. Average: 0.004 ms  
- Creating 200000 channels with asynchronous connect policy took 819 ms. Average: 0.004 ms  
```
Q16: How long does it take for channels to connect asynchronously ? Answer: **See below.**
```
- Connecting 1 channels asynchronously took 20 ms. Average: 20.000 ms.  
- Connecting 10 channels asynchronously took 23 ms. Average: 2.300 ms.  
- Connecting 100 channels asynchronously took 32 ms. Average: 0.320 ms.  
- Connecting 1000 channels asynchronously took 145 ms. Average: 0.145 ms.  
- Connecting 10000 channels asynchronously took 1251 ms. Average: 0.125 ms.  
- Connecting 50000 channels asynchronously took 6642 ms. Average: 0.133 ms.  
- Connecting 100000 channels asynchronously took 11781 ms. Average: 0.118 ms.  
- Connecting 150000 channels asynchronously took 16806 ms. Average: 0.112 ms.  
- Connecting 200000 channels asynchronously took 22120 ms. Average: 0.111 ms.  
```
Q17: What is the cost of performing a synchronous get on multiple channels (same PV) ? Answer: **See below.**
```
- Synchronous Get from 1 channels took 3 ms. Average: 3.000 ms  
- Synchronous Get from 10 channels took 5 ms. Average: 0.500 ms  
- Synchronous Get from 100 channels took 16 ms. Average: 0.160 ms  
- Synchronous Get from 1000 channels took 74 ms. Average: 0.074 ms  
- Synchronous Get from 10000 channels took 664 ms. Average: 0.066 ms  
- Synchronous Get from 20000 channels took 1174 ms. Average: 0.059 ms  
- Synchronous Get from 40000 channels took 2648 ms. Average: 0.066 ms  
- Synchronous Get from 60000 channels took 4073 ms. Average: 0.068 ms  
- Synchronous Get from 80000 channels took 5164 ms. Average: 0.065 ms  
```
Q18: What is the cost of performing an asynchronous get on multiple channels (same PV) ? Answer: **See below.**
```
- Asynchronous Get from 1 channels took 2 ms. Average: 2.000 ms  
- Asynchronous Get from 10 channels took 6 ms. Average: 0.600 ms  
- Asynchronous Get from 100 channels took 29 ms. Average: 0.290 ms  
- Asynchronous Get from 1000 channels took 76 ms. Average: 0.076 ms  
- Asynchronous Get from 10000 channels took 212 ms. Average: 0.021 ms  
- Asynchronous Get from 20000 channels took 243 ms. Average: 0.012 ms  
- Asynchronous Get from 40000 channels took 349 ms. Average: 0.009 ms  
- Asynchronous Get from 60000 channels took 509 ms. Average: 0.008 ms  
- Asynchronous Get from 80000 channels took 663 ms. Average: 0.008 ms  
- Asynchronous Get from 100000 channels took 796 ms. Average: 0.008 ms  
```
Q19: What is the cost of performing an asynchronous get on multiple channels (different PVs) ? Answer: **See below.**
```
- Asynchronous Get from 1 channels took 0 ms. Average: 0.000 ms  
- Asynchronous Get from 10 channels took 7 ms. Average: 0.700 ms  
- Asynchronous Get from 100 channels took 8 ms. Average: 0.080 ms  
- Asynchronous Get from 1000 channels took 48 ms. Average: 0.048 ms  
- Asynchronous Get from 10000 channels took 133 ms. Average: 0.013 ms  
- Asynchronous Get from 20000 channels took 184 ms. Average: 0.009 ms  
- Asynchronous Get from 40000 channels took 306 ms. Average: 0.008 ms  
- Asynchronous Get from 60000 channels took 437 ms. Average: 0.007 ms  
- Asynchronous Get from 80000 channels took 568 ms. Average: 0.007 ms  
- Asynchronous Get from 100000 channels took 723 ms. Average: 0.007 ms  
```
Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**
```
Implementation: BlockingQueueSingleWorkerMonitorNotificationServiceImpl
- Asynchronous Monitor from 1 channels took 25 ms. Average: 25.000 ms  
- Asynchronous Monitor from 100 channels took 36 ms. Average: 0.360 ms  
- Asynchronous Monitor from 200 channels took 37 ms. Average: 0.185 ms  
- Asynchronous Monitor from 500 channels took 40 ms. Average: 0.080 ms  
- Asynchronous Monitor from 1000 channels took 62 ms. Average: 0.062 ms  
```
Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**
```
Implementation: BlockingQueueMultipleWorkerMonitorNotificationServiceImpl
- Asynchronous Monitor from 1 channels took 0 ms. Average: 0.000 ms  
- Asynchronous Monitor from 100 channels took 5 ms. Average: 0.050 ms  
- Asynchronous Monitor from 200 channels took 16 ms. Average: 0.080 ms  
- Asynchronous Monitor from 500 channels took 18 ms. Average: 0.036 ms  
- Asynchronous Monitor from 1000 channels took 22 ms. Average: 0.022 ms 
```
Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**
```
Implementation: StripedExecutorServiceMonitorNotificationServiceImpl
- Asynchronous Monitor from 1 channels took 9 ms. Average: 9.000 ms  
- Asynchronous Monitor from 100 channels took 24 ms. Average: 0.240 ms  
- Asynchronous Monitor from 200 channels took 27 ms. Average: 0.135 ms  
- Asynchronous Monitor from 500 channels took 34 ms. Average: 0.068 ms  
- Asynchronous Monitor from 1000 channels took 45 ms. Average: 0.045 ms  
```
Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**
```
Implementation: BlockingQueueSingleWorkerMonitorNotificationServiceImpl
- Transfer time for integer array of 10000 elements took 1081 ms. Transfer rate: 0.035 MB/s  
- Transfer time for integer array of 20000 elements took 2 ms. Transfer rate: 38.147 MB/s  
- Transfer time for integer array of 50000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 100000 elements took 1 ms. Transfer rate: 381.470 MB/s  
- Transfer time for integer array of 200000 elements took 1 ms. Transfer rate: 762.939 MB/s  
- Transfer time for integer array of 500000 elements took 8 ms. Transfer rate: 238.419 MB/s  
- Transfer time for integer array of 1000000 elements took 15 ms. Transfer rate: 254.313 MB/s  
- Transfer time for integer array of 2000000 elements took 112 ms. Transfer rate: 68.120 MB/s  
- Transfer time for integer array of 5000000 elements took 84 ms. Transfer rate: 227.065 MB/s  
- Transfer time for integer array of 10000000 elements took 134 ms. Transfer rate: 284.679 MB/s  
```
Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**
```
Implementation: BlockingQueueMultipleWorkerMonitorNotificationServiceImpl
Transfer time for integer array of 10000 elements took 222 ms. Transfer rate: 0.172 MB/s  
Transfer time for integer array of 20000 elements took 1 ms. Transfer rate: 76.294 MB/s  
Transfer time for integer array of 50000 elements took 4 ms. Transfer rate: 47.684 MB/s  
Transfer time for integer array of 100000 elements took 1 ms. Transfer rate: 381.470 MB/s  
Transfer time for integer array of 200000 elements took 2 ms. Transfer rate: 381.470 MB/s  
Transfer time for integer array of 500000 elements took 15 ms. Transfer rate: 127.157 MB/s  
Transfer time for integer array of 1000000 elements took 5 ms. Transfer rate: 762.939 MB/s  
Transfer time for integer array of 2000000 elements took 10 ms. Transfer rate: 762.939 MB/s  
Transfer time for integer array of 5000000 elements took 37 ms. Transfer rate: 515.500 MB/s  
Transfer time for integer array of 10000000 elements took 75 ms. Transfer rate: 508.626 MB/s  
```
Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**
```
Implementation: StripedExecutorServiceMonitorNotificationServiceImpl
- Transfer time for integer array of 10000 elements took 62 ms. Transfer rate: 0.615 MB/s  
- Transfer time for integer array of 20000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 50000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 100000 elements took 1 ms. Transfer rate: 381.470 MB/s  
- Transfer time for integer array of 200000 elements took 3 ms. Transfer rate: 254.313 MB/s  
- Transfer time for integer array of 500000 elements took 4 ms. Transfer rate: 476.837 MB/s  
- Transfer time for integer array of 1000000 elements took 7 ms. Transfer rate: 544.957 MB/s  
- Transfer time for integer array of 2000000 elements took 15 ms. Transfer rate: 508.626 MB/s  
- Transfer time for integer array of 5000000 elements took 44 ms. Transfer rate: 433.488 MB/s  
- Transfer time for integer array of 10000000 elements took 123 ms. Transfer rate: 310.138 MB/s  
```

### CA Channels Tests

Q31: What is the cost of synchronously connecting channels (using Channels class) ? Answer: **See below.**
```
- Synchronously connecting 1 channels took 14 ms. Average: 14.000 ms  
- Synchronously connecting 10 channels took 145 ms. Average: 14.500 ms  
- Synchronously connecting 100 channels took 1108 ms. Average: 11.080 ms  
- Synchronously connecting 500 channels took 4860 ms. Average: 9.720 ms  
- Synchronously connecting 1000 channels took 9209 ms. Average: 9.209 ms  
```



