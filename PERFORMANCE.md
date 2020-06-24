# Performance Testing

This document presents the results of integeration testing on the current and previous release. By comparing the 
results it is hoped we may spot perfromance regressions that might otherwise go unnoticed.

Before running the tests an EPICS SoftIOC should be started based on the [epics_tests.db](src/it/resources/epics_tests.db).

```
softIoc -d epics_tests.db 
```

## Testing on Software Release CA-1.3.1

### CA Context Tests

Q1: Can the context manual close feature be relied on to cleanup the created channels ? Answer: **YES** 

Q2: Can the context autoclose feature be relied on to cleanup the created channels ? Answer: **YES**

Q3: How many contexts can be created ? Answer: **at least 150 **  

Q4: What is the context creation cost ? Answer: **See below.**  
```  
- Creating 1 contexts took 17 ms. Average: 17.000 ms  
- Creating 10 contexts took 86 ms. Average: 8.600 ms  
- Creating 50 contexts took 783 ms. Average: 15.660 ms  
- Creating 100 contexts took 2893 ms. Average: 28.930 ms  
- Creating 150 contexts took 5927 ms. Average: 39.513 ms  
```  
Q5: Do all contexts share the same returned object ? Answer: {}.**NO**  
Context object names were as follows:  
```  
- Context object 1 had name: org.epics.ca.Context@2f4099e6  
- Context object 10 had name: org.epics.ca.Context@7bb373a2  
- Context object 50 had name: org.epics.ca.Context@13bfdf3e  
- Context object 100 had name: org.epics.ca.Context@73454c6  
- Context object 150 had name: org.epics.ca.Context@2c10626f  
```  

### CA Channels Tests

Q10: How many channels can be created ? Answer: **at least 1000000**

Q11: What is the channel creation cost ? Answer: **See below.**
```
- Creating 1 channels took 0 ms. Average: 0.000 ms
- Creating 10 channels took 11 ms. Average: 1.100 ms
- Creating 100 channels took 13 ms. Average: 0.130 ms
- Creating 1000 channels took 22 ms. Average: 0.022 ms
- Creating 10000 channels took 67 ms. Average: 0.007 ms
- Creating 50000 channels took 297 ms. Average: 0.006 ms
- Creating 100000 channels took 558 ms. Average: 0.006 ms
- Creating 500000 channels took 1528 ms. Average: 0.003 ms
- Creating 1000000 channels took 2661 ms. Average: 0.003 ms
```
Q12: Do all channels connected to the same PV share the same returned object ? Answer: **NO**.
Channel object names were as follows:
```
- Channel object 1 had name: 'org.epics.ca.impl.ChannelImpl@15a34df2'
- Channel object 10 had name: 'org.epics.ca.impl.ChannelImpl@5b38c1ec'
- Channel object 100 had name: 'org.epics.ca.impl.ChannelImpl@338fc1d8'
- Channel object 1000 had name: 'org.epics.ca.impl.ChannelImpl@4722ef0c'
- Channel object 10000 had name: 'org.epics.ca.impl.ChannelImpl@48e1f6c7'
- Channel object 50000 had name: 'org.epics.ca.impl.ChannelImpl@55cb6996'
- Channel object 100000 had name: 'org.epics.ca.impl.ChannelImpl@1807e3f6'
- Channel object 500000 had name: 'org.epics.ca.impl.ChannelImpl@480d3575'
- Channel object 1000000 had name: 'org.epics.ca.impl.ChannelImpl@f1da57d'
```
Q13: How many connected channels can the library simultaneously support ? Answer: **at least 2000**

Q14: What is the cost of synchronously connecting channels (using Channel class) ? Answer: **See below.**
```
- Synchronously connecting 1 channels took 178 ms. Average: 178.000 ms
- Synchronously connecting 10 channels took 300 ms. Average: 30.000 ms
- Synchronously connecting 100 channels took 1380 ms. Average: 13.800 ms
- Synchronously connecting 500 channels took 5466 ms. Average: 10.932 ms
- Synchronously connecting 1000 channels took 9817 ms. Average: 9.817 ms
- Synchronously connecting 2000 channels took 19858 ms. Average: 9.929 ms
```
Q15: What is the cost of creating channels which will asynchronously connect ? Answer: **See below.**
```
- Creating 1 channels with asynchronous connect policy took 1 ms. Average: 1.000000 ms
- Creating 10 channels with asynchronous connect policy took 1 ms. Average: 0.100000 ms
- Creating 100 channels with asynchronous connect policy took 1 ms. Average: 0.010000 ms
- Creating 1000 channels with asynchronous connect policy took 4 ms. Average: 0.004000 ms
- Creating 10000 channels with asynchronous connect policy took 58 ms. Average: 0.005800 ms
- Creating 50000 channels with asynchronous connect policy took 370 ms. Average: 0.007400 ms
- Creating 100000 channels with asynchronous connect policy took 652 ms. Average: 0.006520 ms
- Creating 150000 channels with asynchronous connect policy took 899 ms. Average: 0.005993 ms
- Creating 200000 channels with asynchronous connect policy took 1259 ms. Average: 0.006295 ms
```
Q16: How long does it take for channels to connect asynchronously ? Answer: **See below.**
```
- Connecting 1 channels asynchronously took 63 ms. Average: 63.000 ms.
- Connecting 10 channels asynchronously took 72 ms. Average: 7.200 ms.
- Connecting 100 channels asynchronously took 73 ms. Average: 0.730 ms.
- Connecting 1000 channels asynchronously took 201 ms. Average: 0.201 ms.
- Connecting 10000 channels asynchronously took 1260 ms. Average: 0.126 ms.
- Connecting 50000 channels asynchronously took 4139 ms. Average: 0.083 ms.
- Connecting 100000 channels asynchronously took 7760 ms. Average: 0.078 ms.
- Connecting 150000 channels asynchronously took 11543 ms. Average: 0.077 ms.
- Connecting 200000 channels asynchronously took 15864 ms. Average: 0.079 ms.
```
Q17: What is the cost of performing a synchronous get on multiple channels (same PV) ? Answer: **See below.**
```
- Synchronous Get from 1 channels took 1 ms. Average: 1.000000 ms
- Synchronous Get from 10 channels took 5 ms. Average: 0.500000 ms
- Synchronous Get from 100 channels took 72 ms. Average: 0.720000 ms
- Synchronous Get from 1000 channels took 760 ms. Average: 0.760000 ms
- Synchronous Get from 10000 channels took 7542 ms. Average: 0.754200 ms
- Synchronous Get from 20000 channels took 12654 ms. Average: 0.632700 ms
- Synchronous Get from 40000 channels took 23729 ms. Average: 0.593225 ms
- Synchronous Get from 60000 channels took 36524 ms. Average: 0.608733 ms
- Synchronous Get from 80000 channels took 50049 ms. Average: 0.625612 ms
```
Q18: What is the cost of performing an asynchronous get on multiple channels (same PV) ? Answer: **See below.**
```
- Asynchronous Get from 1 channels took 19 ms. Average: 19.000000 ms
- Asynchronous Get from 10 channels took 64 ms. Average: 6.400000 ms
- Asynchronous Get from 100 channels took 65 ms. Average: 0.650000 ms
- Asynchronous Get from 1000 channels took 79 ms. Average: 0.079000 ms
- Asynchronous Get from 10000 channels took 253 ms. Average: 0.025300 ms
- Asynchronous Get from 20000 channels took 419 ms. Average: 0.020950 ms
- Asynchronous Get from 40000 channels took 763 ms. Average: 0.019075 ms
- Asynchronous Get from 60000 channels took 1111 ms. Average: 0.018517 ms
- Asynchronous Get from 80000 channels took 1770 ms. Average: 0.022125 ms
- Asynchronous Get from 100000 channels took 1814 ms. Average: 0.018140 ms
```
Q19: What is the cost of performing an asynchronous get on multiple channels (different PVs) ? Answer: **See below.**
```
- Asynchronous Get from 1 channels took 2 ms. Average: 2.000000 ms
- Asynchronous Get from 1000000 channels took 13212 ms. Average: 0.013212 ms
```
Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**
```
BlockingQueueSingleWorker implementation:
- Asynchronous Monitor from 1 channels took 6 ms. Average: 6.000000 ms
- Asynchronous Monitor from 100 channels took 43 ms. Average: 0.430000 ms
- Asynchronous Monitor from 200 channels took 62 ms. Average: 0.310000 ms
- Asynchronous Monitor from 500 channels took 101 ms. Average: 0.202000 ms
- Asynchronous Monitor from 1000 channels took 130 ms. Average: 0.130000 ms
```
Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**
```
BlockingQueueMultipleWorker implementation:
- Asynchronous Monitor from 1 channels took 1 ms. Average: 1.000000 ms
- Asynchronous Monitor from 100 channels took 10 ms. Average: 0.100000 ms
- Asynchronous Monitor from 200 channels took 16 ms. Average: 0.080000 ms
- Asynchronous Monitor from 500 channels took 29 ms. Average: 0.058000 ms
- Asynchronous Monitor from 1000 channels took 46 ms. Average: 0.046000 ms
```
Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**
```
DisruptorOld implementation implementation:
- Asynchronous Monitor from 1 channels took 35 ms. Average: 35.000000 ms
- Asynchronous Monitor from 100 channels took 59 ms. Average: 0.590000 ms
- Asynchronous Monitor from 200 channels took 80 ms. Average: 0.400000 ms
- Asynchronous Monitor from 500 channels took 131 ms. Average: 0.262000 ms
- Asynchronous Monitor from 1000 channels took 286 ms. Average: 0.286000 ms
```
Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**
```
DisruptorNew implementation implementation:
- Asynchronous Monitor from 1 channels took 18 ms. Average: 18.000000 ms
- Asynchronous Monitor from 100 channels took 46 ms. Average: 0.460000 ms
- Asynchronous Monitor from 200 channels took 133 ms. Average: 0.665000 ms
- Asynchronous Monitor from 500 channels took 140 ms. Average: 0.280000 ms
- Asynchronous Monitor from 1000 channels took 399 ms. Average: 0.399000 ms
```
Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**
```
StripedExecutorService implementation:
- Asynchronous Monitor from 1 channels took 16 ms. Average: 16.000000 ms
- Asynchronous Monitor from 100 channels took 38 ms. Average: 0.380000 ms
- Asynchronous Monitor from 200 channels took 40 ms. Average: 0.200000 ms
- Asynchronous Monitor from 500 channels took 49 ms. Average: 0.098000 ms
- Asynchronous Monitor from 1000 channels took 63 ms. Average: 0.063000 ms
```
Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**
```
BlockingQueueSingleWorker implementation:
- Transfer time for integer array of 10000 elements took 37 ms. Transfer rate: 1 MB/s
- Transfer time for integer array of 20000 elements took 4 ms. Transfer rate: 19 MB/s
- Transfer time for integer array of 50000 elements took 2 ms. Transfer rate: 95 MB/s
- Transfer time for integer array of 100000 elements took 5 ms. Transfer rate: 76 MB/s
- Transfer time for integer array of 200000 elements took 10 ms. Transfer rate: 76 MB/s
- Transfer time for integer array of 500000 elements took 24 ms. Transfer rate: 79 MB/s
- Transfer time for integer array of 1000000 elements took 50 ms. Transfer rate: 76 MB/s
- Transfer time for integer array of 2000000 elements took 93 ms. Transfer rate: 82 MB/s
- Transfer time for integer array of 5000000 elements took 186 ms. Transfer rate: 103 MB/s
- Transfer time for integer array of 10000000 elements took 425 ms. Transfer rate: 90 MB/s
```
Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**
```
BlockingQueueMultipleWorker implementation:
- Transfer time for integer array of 10000 elements took 11 ms. Transfer rate: 3 MB/s
- Transfer time for integer array of 20000 elements took 2 ms. Transfer rate: 38 MB/s
- Transfer time for integer array of 50000 elements took 3 ms. Transfer rate: 64 MB/s
- Transfer time for integer array of 100000 elements took 14 ms. Transfer rate: 27 MB/s
- Transfer time for integer array of 200000 elements took 16 ms. Transfer rate: 48 MB/s
- Transfer time for integer array of 500000 elements took 43 ms. Transfer rate: 44 MB/s
- Transfer time for integer array of 1000000 elements took 62 ms. Transfer rate: 62 MB/s
- Transfer time for integer array of 2000000 elements took 84 ms. Transfer rate: 91 MB/s
- Transfer time for integer array of 5000000 elements took 303 ms. Transfer rate: 63 MB/s
- Transfer time for integer array of 10000000 elements took 371 ms. Transfer rate: 103 MB/s
```
Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**
```
DisruptorOld implementation:
- Transfer time for integer array of 10000 elements took 6 ms. Transfer rate: 6 MB/s
- Transfer time for integer array of 20000 elements took 1 ms. Transfer rate: 76 MB/s
- Transfer time for integer array of 50000 elements took 5 ms. Transfer rate: 38 MB/s
- Transfer time for integer array of 100000 elements took 11 ms. Transfer rate: 35 MB/s
- Transfer time for integer array of 200000 elements took 16 ms. Transfer rate: 48 MB/s
- Transfer time for integer array of 500000 elements took 23 ms. Transfer rate: 83 MB/s
- Transfer time for integer array of 1000000 elements took 39 ms. Transfer rate: 98 MB/s
- Transfer time for integer array of 2000000 elements took 62 ms. Transfer rate: 123 MB/s
- Transfer time for integer array of 5000000 elements took 194 ms. Transfer rate: 98 MB/s
- Transfer time for integer array of 10000000 elements took 463 ms. Transfer rate: 82 MB/s
```
Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**
```
DisruptorNew implementation:
- Transfer time for integer array of 10000 elements took 5 ms. Transfer rate: 8 MB/s
- Transfer time for integer array of 20000 elements took 1 ms. Transfer rate: 76 MB/s
- Transfer time for integer array of 50000 elements took 1 ms. Transfer rate: 191 MB/s
- Transfer time for integer array of 100000 elements took 3 ms. Transfer rate: 127 MB/s
- Transfer time for integer array of 200000 elements took 9 ms. Transfer rate: 85 MB/s
- Transfer time for integer array of 500000 elements took 22 ms. Transfer rate: 87 MB/s
- Transfer time for integer array of 1000000 elements took 39 ms. Transfer rate: 98 MB/s
- Transfer time for integer array of 2000000 elements took 122 ms. Transfer rate: 63 MB/s
- Transfer time for integer array of 5000000 elements took 210 ms. Transfer rate: 91 MB/s
- Transfer time for integer array of 10000000 elements took 335 ms. Transfer rate: 114 MB/s
```

Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**
```
StripedExecutorService implementation:
- Transfer time for integer array of 10000 elements took 6 ms. Transfer rate: 6 MB/s
- Transfer time for integer array of 20000 elements took 1 ms. Transfer rate: 76 MB/s
- Transfer time for integer array of 50000 elements took 1 ms. Transfer rate: 191 MB/s
- Transfer time for integer array of 100000 elements took 3 ms. Transfer rate: 127 MB/s
- Transfer time for integer array of 200000 elements took 7 ms. Transfer rate: 109 MB/s
- Transfer time for integer array of 500000 elements took 16 ms. Transfer rate: 119 MB/s
- Transfer time for integer array of 1000000 elements took 36 ms. Transfer rate: 106 MB/s
- Transfer time for integer array of 2000000 elements took 91 ms. Transfer rate: 84 MB/s
- Transfer time for integer array of 5000000 elements took 202 ms. Transfer rate: 94 MB/s
- Transfer time for integer array of 10000000 elements took 351 ms. Transfer rate: 109 MB/s
```

### CA Channels Tests

Q31: What is the cost of synchronously connecting channels (using Channels class) ? Answer: **See below.**
```
- Synchronously connecting 1 channels took 11 ms. Average: 11.000 ms
- Synchronously connecting 10 channels took 60 ms. Average: 6.000 ms
- Synchronously connecting 100 channels took 1255 ms. Average: 12.550 ms
- Synchronously connecting 500 channels took 5720 ms. Average: 11.440 ms
- Synchronously connecting 1000 channels took 10686 ms. Average: 10.686 ms
```



