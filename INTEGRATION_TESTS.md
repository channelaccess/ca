# Performance Testing

This document presents the results of integration testing on the current and previous release. By comparing the 
results with previous releases it is hoped we may optimse the performance of the library and/or spot performance regressions that might otherwise go unnoticed.

The performance tests were written from the persoective of a developer who is first starting to use the __ca__. Each test presents the answer to a genuine question that a new maintainer of the library once had.

Before running the tests an EPICS SoftIOC should be started based on the [epics_tests.db](src/integrationTest/resources/epics_tests.db).

```
export EPICS_CA_MAX_ARRAY_BYTES=1000000000
softIoc -d epics_tests.db 
```

## Testing on Software Release CA-1.3.2

 * Date: 2020-06-27
 * EPICS Base Version used for SoftIOC: 7.0.4.1-DEV
 * IOC Test Platform: MacBook Pro 2018 vintage.  
 * CA Library Test Platform: MacBook Pro 2018 vintage.  
 
### CA Context Tests

Q1: Can the context manual close feature be relied on to cleanup the created channels ? Answer: **YES**.  

Q2: Can the context autoclose feature be relied on to cleanup the created channels ? Answer: **YES**.  

Q3: How many contexts can be created ? Answer: **at least 150**.  ,

Q4: What is the context creation cost ? Answer: **See below.**  
```  
- Creating 1 contexts took 17 ms. Average: 17.000 ms  
- Creating 10 contexts took 93 ms. Average: 9.300 ms  
- Creating 50 contexts took 661 ms. Average: 13.220 ms  
- Creating 100 contexts took 2286 ms. Average: 22.860 ms  
- Creating 150 contexts took 6359 ms. Average: 42.393 ms  
```  

Q5: Do all contexts share the same returned object ? Answer: **NO**.  
Context object names were as follows:  
```  
- Context object 1 had name: org.epics.ca.Context@296d4853  
- Context object 10 had name: org.epics.ca.Context@26059b34  
- Context object 50 had name: org.epics.ca.Context@5942e34b  
- Context object 100 had name: org.epics.ca.Context@4ddc8c1a  
- Context object 150 had name: org.epics.ca.Context@e306a22  
```  

### CA Channel Tests
Q10: How many channels can be created ? Answer: **at least 400000**.  

Q11: What is the channel creation cost ? Answer: **See below.**  
```  
- Creating 1 channels took 0 ms. Average: 0.000 ms  
- Creating 10 channels took 0 ms. Average: 0.000 ms  
- Creating 100 channels took 2 ms. Average: 0.020 ms  
- Creating 1000 channels took 33 ms. Average: 0.033 ms  
- Creating 10000 channels took 113 ms. Average: 0.011 ms  
- Creating 50000 channels took 264 ms. Average: 0.005 ms  
- Creating 100000 channels took 451 ms. Average: 0.005 ms  
- Creating 200000 channels took 611 ms. Average: 0.003 ms  
- Creating 400000 channels took 3119 ms. Average: 0.008 ms  
```

Q12: Do all channels connected to the same PV share the same returned object ? Answer: **NO**.  
Channel object names were as follows:  
```  
- Channel object 1 had name: 'org.epics.ca.impl.ChannelImpl@577086a9'  
- Channel object 10 had name: 'org.epics.ca.impl.ChannelImpl@366471cd'  
- Channel object 100 had name: 'org.epics.ca.impl.ChannelImpl@617a75b'  
- Channel object 1000 had name: 'org.epics.ca.impl.ChannelImpl@4d8f4d1b'  
- Channel object 10000 had name: 'org.epics.ca.impl.ChannelImpl@1e99dfcd'  
- Channel object 50000 had name: 'org.epics.ca.impl.ChannelImpl@b663fca'  
- Channel object 100000 had name: 'org.epics.ca.impl.ChannelImpl@30f07c82'  
- Channel object 200000 had name: 'org.epics.ca.impl.ChannelImpl@7a00812'  
- Channel object 400000 had name: 'org.epics.ca.impl.ChannelImpl@5797c89c'  
```  

Q13: How many connected channels can the library simultaneously support ? Answer: **at least 2000**.  

Q14: What is the cost of synchronously connecting channels (using Channel class) ? Answer: **See below.**  
```  
- Synchronously connecting 1 channels took 6 ms. Average: 6.000 ms  
- Synchronously connecting 10 channels took 48 ms. Average: 4.800 ms  
- Synchronously connecting 100 channels took 922 ms. Average: 9.220 ms  
- Synchronously connecting 500 channels took 4768 ms. Average: 9.536 ms  
- Synchronously connecting 1000 channels took 9166 ms. Average: 9.166 ms  
- Synchronously connecting 2000 channels took 17587 ms. Average: 8.793 ms  
```  

Q15: What is the cost of creating channels which will asynchronously connect ? Answer: **See below.**  
```  
- Creating 1 channels with asynchronous connect policy took 3 ms. Average: 3.000 ms  
- Creating 10 channels with asynchronous connect policy took 3 ms. Average: 0.300 ms  
- Creating 100 channels with asynchronous connect policy took 4 ms. Average: 0.040 ms  
- Creating 1000 channels with asynchronous connect policy took 8 ms. Average: 0.008 ms  
- Creating 10000 channels with asynchronous connect policy took 47 ms. Average: 0.005 ms  
- Creating 50000 channels with asynchronous connect policy took 193 ms. Average: 0.004 ms  
- Creating 100000 channels with asynchronous connect policy took 341 ms. Average: 0.003 ms  
- Creating 150000 channels with asynchronous connect policy took 489 ms. Average: 0.003 ms  
- Creating 200000 channels with asynchronous connect policy took 652 ms. Average: 0.003 ms  
```  

Q16: How long does it take for channels to connect asynchronously ? Answer: **See below.**  
```  
- Connecting 1 channels asynchronously took 12 ms. Average: 12.000 ms.  
- Connecting 10 channels asynchronously took 19 ms. Average: 1.900 ms.  
- Connecting 100 channels asynchronously took 40 ms. Average: 0.400 ms.  
- Connecting 1000 channels asynchronously took 123 ms. Average: 0.123 ms.  
- Connecting 10000 channels asynchronously took 2050 ms. Average: 0.205 ms.  
- Connecting 50000 channels asynchronously took 6054 ms. Average: 0.121 ms.  
- Connecting 100000 channels asynchronously took 10977 ms. Average: 0.110 ms.  
- Connecting 150000 channels asynchronously took 16187 ms. Average: 0.108 ms.  
- Connecting 200000 channels asynchronously took 21465 ms. Average: 0.107 ms.  
```   

Q17: What is the cost of performing a synchronous get on multiple channels (same PV) ? Answer: **See below.**  
```  
- Synchronous Get from 1 channels took 4 ms. Average: 4.000 ms  
- Synchronous Get from 10 channels took 7 ms. Average: 0.700 ms  
- Synchronous Get from 100 channels took 23 ms. Average: 0.230 ms  
- Synchronous Get from 1000 channels took 86 ms. Average: 0.086 ms  
- Synchronous Get from 10000 channels took 696 ms. Average: 0.070 ms  
- Synchronous Get from 20000 channels took 1339 ms. Average: 0.067 ms  
- Synchronous Get from 40000 channels took 2703 ms. Average: 0.068 ms  
- Synchronous Get from 60000 channels took 4131 ms. Average: 0.069 ms  
- Synchronous Get from 80000 channels took 5704 ms. Average: 0.071 ms  
```   

Q18: What is the cost of performing an asynchronous get on multiple channels (same PV) ? Answer: **See below.**  
```  
- Asynchronous Get from 1 channels took 0 ms. Average: 0.000 ms  
- Asynchronous Get from 10 channels took 2 ms. Average: 0.200 ms  
- Asynchronous Get from 100 channels took 7 ms. Average: 0.070 ms  
- Asynchronous Get from 1000 channels took 27 ms. Average: 0.027 ms  
- Asynchronous Get from 10000 channels took 146 ms. Average: 0.015 ms  
- Asynchronous Get from 20000 channels took 265 ms. Average: 0.013 ms  
- Asynchronous Get from 40000 channels took 435 ms. Average: 0.011 ms  
- Asynchronous Get from 60000 channels took 608 ms. Average: 0.010 ms  
- Asynchronous Get from 80000 channels took 754 ms. Average: 0.009 ms  
- Asynchronous Get from 100000 channels took 887 ms. Average: 0.009 ms  
```  

Q19: What is the cost of performing an asynchronous get on multiple channels (different PVs) ? Answer: **See below.**  
```  
- Asynchronous Get from 1 channels took 1 ms. Average: 1.000 ms  
- Asynchronous Get from 10 channels took 2 ms. Average: 0.200 ms  
- Asynchronous Get from 100 channels took 4 ms. Average: 0.040 ms  
- Asynchronous Get from 1000 channels took 17 ms. Average: 0.017 ms  
- Asynchronous Get from 10000 channels took 95 ms. Average: 0.009 ms  
- Asynchronous Get from 20000 channels took 175 ms. Average: 0.009 ms  
- Asynchronous Get from 40000 channels took 293 ms. Average: 0.007 ms  
- Asynchronous Get from 60000 channels took 458 ms. Average: 0.008 ms  
- Asynchronous Get from 80000 channels took 610 ms. Average: 0.008 ms  
- Asynchronous Get from 100000 channels took 741 ms. Average: 0.007 ms  
```  

Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**  
```  
Implementation: BlockingQueueSingleWorkerMonitorNotificationServiceImpl  
- Asynchronous Monitor from 1 channels took 11 ms. Average: 11.000 ms  
- Asynchronous Monitor from 100 channels took 29 ms. Average: 0.290 ms  
- Asynchronous Monitor from 200 channels took 40 ms. Average: 0.200 ms  
- Asynchronous Monitor from 500 channels took 63 ms. Average: 0.126 ms  
- Asynchronous Monitor from 1000 channels took 97 ms. Average: 0.097 ms  
```  

Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**  
```  
Implementation: BlockingQueueMultipleWorkerMonitorNotificationServiceImpl  
- Asynchronous Monitor from 1 channels took 0 ms. Average: 0.000 ms  
- Asynchronous Monitor from 100 channels took 4 ms. Average: 0.040 ms  
- Asynchronous Monitor from 200 channels took 8 ms. Average: 0.040 ms  
- Asynchronous Monitor from 500 channels took 23 ms. Average: 0.046 ms  
- Asynchronous Monitor from 1000 channels took 58 ms. Average: 0.058 ms  
```  

Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**  
```  
Implementation: StripedExecutorServiceMonitorNotificationServiceImpl  
- Asynchronous Monitor from 1 channels took 8 ms. Average: 8.000 ms  
- Asynchronous Monitor from 100 channels took 14 ms. Average: 0.140 ms  
- Asynchronous Monitor from 200 channels took 18 ms. Average: 0.090 ms  
- Asynchronous Monitor from 500 channels took 29 ms. Average: 0.058 ms  
- Asynchronous Monitor from 1000 channels took 43 ms. Average: 0.043 ms  
```  

Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**  
```  
Implementation: BlockingQueueSingleWorkerMonitorNotificationServiceImpl  
- Transfer time for integer array of 10000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 20000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 50000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 100000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 200000 elements took 1 ms. Transfer rate: 762.939 MB/s  
- Transfer time for integer array of 500000 elements took 4 ms. Transfer rate: 476.837 MB/s  
- Transfer time for integer array of 1000000 elements took 27 ms. Transfer rate: 141.285 MB/s  
- Transfer time for integer array of 2000000 elements took 14 ms. Transfer rate: 544.957 MB/s  
- Transfer time for integer array of 5000000 elements took 36 ms. Transfer rate: 529.819 MB/s  
- Transfer time for integer array of 10000000 elements took 123 ms. Transfer rate: 310.138 MB/s  
```  

Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**  
```  
Implementation: BlockingQueueMultipleWorkerMonitorNotificationServiceImpl  
- Transfer time for integer array of 10000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 20000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 50000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 100000 elements took 1 ms. Transfer rate: 381.470 MB/s  
- Transfer time for integer array of 200000 elements took 1 ms. Transfer rate: 762.939 MB/s  
- Transfer time for integer array of 500000 elements took 3 ms. Transfer rate: 635.783 MB/s  
- Transfer time for integer array of 1000000 elements took 21 ms. Transfer rate: 181.652 MB/s  
- Transfer time for integer array of 2000000 elements took 14 ms. Transfer rate: 544.957 MB/s  
- Transfer time for integer array of 5000000 elements took 55 ms. Transfer rate: 346.791 MB/s  
- Transfer time for integer array of 10000000 elements took 211 ms. Transfer rate: 180.791 MB/s  
```  

Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**  
```  
Implementation: StripedExecutorServiceMonitorNotificationServiceImpl  
- Transfer time for integer array of 10000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 20000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 50000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 100000 elements took 0 ms. Transfer rate: Infinity MB/s  
- Transfer time for integer array of 200000 elements took 1 ms. Transfer rate: 762.939 MB/s  
- Transfer time for integer array of 500000 elements took 2 ms. Transfer rate: 953.674 MB/s  
- Transfer time for integer array of 1000000 elements took 16 ms. Transfer rate: 238.419 MB/s  
- Transfer time for integer array of 2000000 elements took 21 ms. Transfer rate: 363.305 MB/s  
- Transfer time for integer array of 5000000 elements took 36 ms. Transfer rate: 529.819 MB/s  
- Transfer time for integer array of 10000000 elements took 89 ms. Transfer rate: 428.618 MB/s  
```  

### CA Channels Tests

Q31: What is the cost of synchronously connecting channels (using Channels class) ? Answer: **See below.**  
```  
- Synchronously connecting 1 channels took 12 ms. Average: 12.000 ms  
- Synchronously connecting 10 channels took 52 ms. Average: 5.200 ms  
- Synchronously connecting 100 channels took 880 ms. Average: 8.800 ms  
- Synchronously connecting 500 channels took 4765 ms. Average: 9.530 ms  
- Synchronously connecting 1000 channels took 9621 ms. Average: 9.621 ms  
```  

