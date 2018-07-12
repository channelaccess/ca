package org.epics.ca;

import org.apache.commons.lang3.time.StopWatch;
import org.epics.ca.impl.BroadcastTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

class ChannelThroughputTests
{
   // Get Logger
   private static final Logger logger = Logger.getLogger( ChannelThroughputTests.class.getName() );

   private Context context;
   private CAJTestServer server;

   @BeforeAll
   static void beforeAll()
   {
      System.setProperty( "java.util.logging.SimpleFormatter.format", "%1$tF %1$tT.%1$tL %4$s  %5$s%6$s%n");
      Locale.setDefault( Locale.ROOT );
   }

   @BeforeEach
   void setUp()
   {
      server = new CAJTestServer();
      server.runInSeparateThread();
      context = new Context();
   }

   @AfterEach
   void tearDown()
   {
      context.close();
      server.destroy();
   }

   @Test
   void TestGet()
   {
      logger.log(Level.INFO, "Get throughput test..." );
      final Channel<Double> channel = context.createChannel("adc01", Double.class);
      channel.connect();

      final List<Integer> samplePoints = Arrays.asList(1, 10, 100, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000);
      final int maxChannels = samplePoints.get(samplePoints.size() - 1);
      final Map<Integer, Long> resultMap = new LinkedHashMap<>();
      final StopWatch stopWatch = StopWatch.createStarted();

      for ( int i = 1; i <= maxChannels; i++ )
      {
         try
         {
            channel.get();
            if ( samplePoints.contains(i) )
            {
               resultMap.put(i, stopWatch.getTime( TimeUnit.MICROSECONDS) );
            }
         }
         catch( Throwable ex )
         {
            logger.info("Test terminated due to exception after getting from {} channels" + String.valueOf( i ) );
         }
      }

      for ( int result : resultMap.keySet() )
      {
         logger.log( Level.INFO, String.format( "- Synchronous Get from '%s' channels took '%s' ms. Average: '%3f' us", result, resultMap.get(result), (float) resultMap.get(result) / result) );
      }

   }

   @Test
   void TestPutAndGet()
   {
      logger.info( "PutAndGet throughput test..." );
      final Channel<Double> channel = context.createChannel("adc01", Double.class);
      channel.connect();
      final List<Integer> samplePoints = Arrays.asList(1, 10, 100, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000);
      final int maxChannels = samplePoints.get(samplePoints.size() - 1);
      final Map<Integer, Long> resultMap = new LinkedHashMap<>();
      final StopWatch stopWatch = StopWatch.createStarted();

      for ( int i = 1; i <= maxChannels; i++ )
      {
         try
         {
            channel.put( (double) i );
            channel.get();
            if ( samplePoints.contains(i) )
            {
               resultMap.put(i, stopWatch.getTime( TimeUnit.MICROSECONDS) );
            }
         }
         catch ( Throwable ex )
         {
            logger.info("Test terminated due to exception after getting from {} channels" + String.valueOf( i ) );
         }
      }

      logger.info( "RESULTS:" );
      for ( int result : resultMap.keySet() )
      {
         logger.log( Level.INFO, String.format( "- Synchronous PuAndGet from '%s' channels took '%s' ms. Average: '%3f' us", result, resultMap.get (result ), (float) resultMap.get(result) / result) );
      }
   }

   @ParameterizedTest
   @ValueSource( strings = { "SingleWorkerBlockingQueueMonitorNotificationServiceImpl",
                             "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl",
                             "DisruptorMonitorNotificationServiceOldImpl",
                             "DisruptorMonitorNotificationServiceNewImpl" } )
   void TestPutAndMonitor( String monitorNotificationServiceImpl )
   {
      logger.log( Level.INFO, String.format("Starting PutAndMonitor throughput test using impl: '%s'...", monitorNotificationServiceImpl ) );

      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER", monitorNotificationServiceImpl );
      final Context mySpecialContext = new Context( contextProperties );

      final List<Integer> samplePoints = Arrays.asList(1, 10, 100, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000);
      final int maxChannels = samplePoints.get(samplePoints.size() - 1);
      final Map<Integer, Long> resultMap1 = new LinkedHashMap<>();
      final Map<Integer, Long> resultMap2 = new LinkedHashMap<>();

      final Channel<Integer> channel = mySpecialContext.createChannel("adc01", Integer.class);
      channel.connect();

      final StopWatch stopWatch = new StopWatch();

      final Monitor<Integer> monitor = channel.addValueMonitor( v -> {
         if ( samplePoints.contains( v ) )
         {
            resultMap2.put( v, stopWatch.getTime( TimeUnit.MICROSECONDS) );
         }
      } );

      //monitor.
      stopWatch.start();
      for ( int i = 1; i <= maxChannels; i++ )
      {
         try
         {
            if ( samplePoints.contains(i) )
            {
               resultMap1.put( i, stopWatch.getTime( TimeUnit.MICROSECONDS) );
            }
            channel.put( i );
         }
         catch ( Throwable ex )
         {
            logger.info("Test terminated due to exception after putting to {} channels" + String.valueOf( i ) );
         }
      }

      // Free up resources
      monitor.close();

      logger.info( "RESULTS:" );
      for ( int result : resultMap1.keySet() )
      {
         long latency = ( resultMap1.containsKey( result ) && resultMap2.containsKey( result ) ) ? resultMap2.get( result) - resultMap1.get( result ) : 0L;
         logger.log( Level.INFO, String.format( "- Synchronous PutAndMonitor from '%s' channels took '%s' ms. Average: '%3f' us. Latency '%s' us", result, resultMap1.get( result), (float) resultMap1.get( result ) / result, latency ) );
      }
   }

   @ParameterizedTest
   @ValueSource( strings = { "SingleWorkerBlockingQueueMonitorNotificationServiceImpl",
                             "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl",
                             "DisruptorMonitorNotificationServiceOldImpl",
                             "DisruptorMonitorNotificationServiceNewImpl" } )
   void TestFastCounterMonitor( String monitorNotificationServiceImpl ) throws InterruptedException
   {
      logger.log( Level.INFO, String.format( "Starting TestFastCounterMonitor throughput test using impl: '%s'...", monitorNotificationServiceImpl ) );

      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER", monitorNotificationServiceImpl );
      final Context mySpecialContext = new Context( contextProperties );

      final List<Integer> samplePoints = Arrays.asList( 100, 200, 500, 1_000, 5_000, 10_000 );
      final Map<Integer, Long> resultMap = new LinkedHashMap<>();
      final Channel<Integer> channel = mySpecialContext.createChannel("fastCounter", Integer.class);
      channel.connect();

      final StopWatch stopWatch = new StopWatch();

      final int start = Integer.MIN_VALUE + 1000;
      final Monitor<Integer> monitor = channel.addValueMonitor( v -> {
         //logger.info( "Thread = {}; Count = {} ", Thread.currentThread().getName(), String.valueOf( v -start ) );
         int count = v - start;
         if ( v == start )
         {
            logger.info( "Starting counting..." );
            stopWatch.start();
         }
         if ( samplePoints.contains( count ) )
         {
            //logger.info( "Count = " + count );
            //logger.info( "Time = " + stopWatch.getTime( TimeUnit.MILLISECONDS) );
            resultMap.put( count, stopWatch.getTime( TimeUnit.MILLISECONDS) );
         }
      } );

      // Wait for results to be available
      Thread.sleep( 20_000 );

      // Free up resources
      monitor.close();

      logger.info( "RESULTS:" );
      for ( int result : resultMap.keySet() )
      {
         logger.log( Level.INFO, String.format( "- TestFastCounterMonitor from '%s' channels took '%s' ms. Average: '%3f' ms", result, resultMap.get(result),(float) resultMap.get(result) / result ) );
      }
   }

}
