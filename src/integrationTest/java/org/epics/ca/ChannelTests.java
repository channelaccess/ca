/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.time.StopWatch;
import org.epics.ca.impl.repeater.NetworkUtilities;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.logging.Logger;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.fail;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

@SuppressWarnings( "BusyWait" )
@TestMethodOrder( MethodOrderer.Alphanumeric.class)
class ChannelTests
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private final Logger logger = LibraryLogManager.getLogger( ChannelTests.class );
   private Context context;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Class methods ------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-private methods --------------------------------------------------*/

   @BeforeAll
   static void beforeAll()
   {
      // This is a guard condition. There is no point in checking the behaviour
      // of the CARepeaterStarterTest class if the network stack is not appropriately
      // configured for channel access.
      assertThat( NetworkUtilities.verifyTargetPlatformNetworkStackIsChannelAccessCompatible(), is(true ) );

      // Currently (2020-05-22) this test is not supported when the VPN connection is active on the local machine.
      if ( NetworkUtilities.isVpnActive() )
      {
         fail( "This test is not supported when a VPN connection is active on the local network interface." );
      }
   }

   @BeforeEach
   void beforeEach() throws Exception
   {
      // The following sleep is sometimes useful to allow profiling with tools like
      // jvisualvm (provides some time for the tool to connect).
      // Thread.sleep( 10000 );

      // Every test involves the use of at least one context so it is better to
      // set them up and close them down outside the test.

      logger.fine( "Creating CA context..." );
      context = new Context();
      logger.fine( "Done." );

      // Check the database is online
      try
      {
         context.createChannel("ca:test:db_ok", String.class).connectAsync().get(5, TimeUnit.SECONDS);
      }
      catch ( TimeoutException ex )
      {
         logger.warning( "The EPICS test database 'epics_tests.db' was not discoverable on the local network." );
         logger.warning( "Please ensure that it is running and available on the network before restarting these tests. " );
         throw new RuntimeException( "EPICS Test Database Not Available - can't run tests !" );
      }
   }

   @AfterEach
   void afterEach()
   {
      logger.fine( "Cleaning up context..." );
      context.close();
      logger.fine( "Done." );
   }

   /**
    * Q10: How many channels can be created ?
    * Q11: What is the creation cost ?
    * Q12: Do all channels connected to the same PV share the same returned object ?
    */
   @Test
   void q10q11q12()
   {
      logger.fine( "Performing Q10/Q11/Q12 Tests: please wait...");

      final List<Integer> samplePoint = Arrays.asList( 1, 10, 100, 1_000, 10_000, 50_000, 100_000, 200_000, 400_000 );
      final Map<Integer, Long> resultMap = new LinkedHashMap<>();
      final LinkedHashMap<Integer, Channel<String>> channelObjectMap = new LinkedHashMap<>();

      final StopWatch stopWatch = StopWatch.createStarted();
      int loopCounter = 0;

      while ( loopCounter < samplePoint.get( samplePoint.size() - 1 ) )
      {
         loopCounter++;
         try
         {
            if ( samplePoint.contains(loopCounter) )
            {
               // For sampling points the channels all have the same name. This allows us to check later
               // whether they apply onto the same object.
               final Channel<String> caChannel = context.createChannel("channel_name_will_be_the same", String.class );
               resultMap.put(loopCounter, stopWatch.getTime());
               channelObjectMap.put(loopCounter, caChannel);
               logger.fine( String.format( "Created %d channels.", loopCounter ) );
            }
            else
            {
               // For non-sampling points the channels have unique names. This is more typical
               // of real world usage.
               context.createChannel("channel_name_will_be_different" + loopCounter, String.class );
            }
         }
         catch ( Throwable ex )
         {
            logger.fine(String.format( "Test terminated due to exception after creating %d channels", loopCounter ) );
            break;
         }
      }

      logger.info( "RESULTS:" );
      logger.info( String.format( "Q10: How many channels can be created ? Answer: **at least %d**.", loopCounter ) );
      logger.info( "Q11: What is the channel creation cost ? Answer: **See below.**" );
      for ( int result : resultMap.keySet() )
      {
         logger.info( String.format( "- Creating %d channels took %d ms. Average: %.3f ms", result, resultMap.get(result), (float) resultMap.get(result) / result ) );
      }

      logger.info( String.format( "Q12: Do all channels connected to the same PV share the same returned object ? Answer: %s", resultMap.size() == channelObjectMap.size() ? "**NO**." : "**YES**." ) );
      logger.info( "Channel object names were as follows:");
      logger.info("```");
      for ( int sampleNumber : channelObjectMap.keySet() )
      {
         logger.info( String.format( "- Channel object %d had name: '%s'", sampleNumber, channelObjectMap.get( sampleNumber ) ) );
      }
      logger.info("```");
   }

   /**
    * Q13: How many connected channels can the library simultaneously support ?
    * Q14: What is the cost of synchronously connecting channels (using Channel class) ?
    */
   @Test
   void q13q14()
   {
      logger.fine( "Performing Q13/Q14 Tests: please wait...");

      final List<Integer> samplePoints = Arrays.asList(1, 10, 100, 500, 1_000, 2_000 );
      final int maxChannels = samplePoints.get(samplePoints.size() - 1);
      final Map<Integer, Long> resultMap = new LinkedHashMap<>();
      final StopWatch stopWatch = StopWatch.createStarted();
      int loopCounter = 0;

      while ( loopCounter < maxChannels )
      {
         loopCounter++;
         try
         {
            final Channel<String> caChannel = context.createChannel("ca:test:counter01", String.class);
            caChannel.connect();
            if ( samplePoints.contains(loopCounter) )
            {
               resultMap.put( loopCounter, stopWatch.getTime());
               logger.fine( String.format( "Synchronously connected %d channels.", loopCounter ));
            }
         }
         catch ( Throwable ex )
         {
            logger.warning( String.format( "Test terminated due to exception after synchronously connecting %d channels",loopCounter ));
         }
      }

      logger.info( "RESULTS:" );
      logger.info( String.format( "Q13: How many connected channels can the library simultaneously support ? Answer: **at least %d**.", loopCounter ) );
      logger.info( "Q14: What is the cost of synchronously connecting channels (using Channel class) ? Answer: **See below.**");
      logger.info("```");
      for ( int result : resultMap.keySet() )
      {
         logger.info( String.format("- Synchronously connecting %d channels took %d ms. Average: %.3f ms", result, resultMap.get(result), (float) resultMap.get(result) / result ) );
      }
      logger.info("```");
   }

   /**
    * Q15: What is the cost of creating channels which will asynchronously connect ?
    * Q16: How long does it take for channels to connect asynchronously ?
    */
   @Test
   void q15q16() throws InterruptedException
   {
      logger.fine( "Performing Q15/Q16 Tests: please wait..." );

      final List<Integer> samplePoints = Arrays.asList(1, 10, 100, 1_000, 10_000, 50_000, 100_000, 150_000, 200_000);
      int maxChannels = samplePoints.get(samplePoints.size() - 1);

      final Map<Integer, Long> creationTimeResultMap = new LinkedHashMap<>();
      final Map<Integer, Long> connectionTimeResultMap = new LinkedHashMap<>();
      final StopWatch stopWatch = StopWatch.createStarted();
      int loopCounter = 0;

      final AtomicInteger connectionCount = new AtomicInteger(0);

      while ( loopCounter < maxChannels )
      {
         loopCounter++;
         try
         {
            final Channel<String> caChannel = context.createChannel("ca:test:counter01", String.class);
            caChannel.addConnectionListener(( c, b ) -> {
               final int count = connectionCount.incrementAndGet();
               if ( samplePoints.contains(count) )
               {
                  connectionTimeResultMap.put(count, stopWatch.getTime());
                  logger.fine( String.format( "Connected %d async channels", count ) );
               }
            });
            caChannel.connectAsync();

            if ( samplePoints.contains(loopCounter) )
            {
               creationTimeResultMap.put(loopCounter, stopWatch.getTime());
               logger.fine( String.format( "Created %d async channels", loopCounter ));
            }
         }
         catch ( Throwable ex )
         {
            logger.warning( String.format( "Test terminated due to exception after creating and asynchronously connecting %d channels", loopCounter ) );
            break;
         }
      }
      while ( connectionCount.get() < maxChannels )
      {
         Thread.sleep(100);
      }

      logger.info( "RESULTS:" );
      logger.info( "Q15: What is the cost of creating channels which will asynchronously connect ? Answer: **See below.**" );
      logger.info( "```" );
      for ( int result : creationTimeResultMap.keySet() )
      {
         logger.info( String.format( "- Creating %d channels with asynchronous connect policy took %d ms. Average: %.3f ms", result, creationTimeResultMap.get(result), (float) creationTimeResultMap.get(result) / result ) );
      }
      logger.info("```");

      logger.info( "Q16: How long does it take for channels to connect asynchronously ? Answer: **See below.**" );
      logger.info( "```" );
      for ( int result : connectionTimeResultMap.keySet() )
      {
         logger.info( String.format( "- Connecting %d channels asynchronously took %d ms. Average: %.3f ms.", result, connectionTimeResultMap.get(result), (float) connectionTimeResultMap.get(result) / result ) );
      }
      logger.info("```");
   }

   /**
    * Q17: What is the cost of performing a synchronous get on multiple channels (all on the same PV) ?
    */
   @Test
   void q17() throws InterruptedException
   {
      logger.fine( "Performing Q17 Test: please wait..." );
      final List<Integer> samplePoints = Arrays.asList(1, 10, 100, 1_000, 10_000, 20_000, 40_000, 60_000, 80_000, 100_000);
      final int maxChannels = samplePoints.get(samplePoints.size() - 1);

      final Map<Integer, Long> resultMap = new LinkedHashMap<>();
      final List<Channel<String>> channelList = new ArrayList<>();
      final AtomicInteger connectionCount = new AtomicInteger(0);

      logger.fine( String.format( "Creating and asynchronously connecting %d channels...", maxChannels ) );

      for ( int i = 0; i < maxChannels; i++ )
      {
         try
         {
            final Channel<String> caChannel = context.createChannel("ca:test:counter01", String.class);
            channelList.add(caChannel);
            caChannel.addConnectionListener(( c, b ) -> connectionCount.incrementAndGet());
            caChannel.connectAsync();
         }
         catch ( Throwable ex )
         {
            logger.warning( String.format( "Test terminated due to exception after creating and asynchronously connecting %d channels", i ) );
            return;
         }
      }
      logger.fine( String.format( "%d channels created.", maxChannels ));

      while ( connectionCount.get() < maxChannels )
      {
         Thread.sleep(100);
      }
      logger.fine( String.format( "%d channels connected.",  maxChannels ) );
      logger.fine( "Performing synchronous get on all channels..." );
      final StopWatch stopWatch = StopWatch.createStarted();
      for ( int i = 0; i < maxChannels; i++ )
      {
         try
         {
            final Channel<String> channel = channelList.get(i);
            channel.get();
            if ( samplePoints.contains(i) )
            {
               resultMap.put(i, stopWatch.getTime());
               logger.fine( String.format( "Synchronous Get completed on %d channels" , i ) );
            }
         }
         catch ( Throwable ex )
         {
            logger.fine( String.format( "Test terminated due to exception after getting from %d channels", i ) );
         }
      }

      logger.info( "RESULTS:" );
      logger.info( "Q17: What is the cost of performing a synchronous get on multiple channels (same PV) ? Answer: **See below.**" );
      logger.info( "```" );
      for ( int result : resultMap.keySet() )
      {
         logger.info( String.format( "- Synchronous Get from %d channels took %d ms. Average: %.3f ms", result, resultMap.get(result), (float) resultMap.get(result) / result ) );
      }
      logger.info("```");
   }

   /**
    * Q18: What is the cost of performing an asynchronous get on multiple channels (all on the same PV) ?
    */
   @Test
   void q18() throws InterruptedException
   {
      logger.fine( "Performing Q18 Test: please wait..." );

      final List<Integer> samplePoints = Arrays.asList(1, 10, 100, 1_000, 10_000, 20_000, 40_000, 60_000, 80_000, 100_000);
      final int maxChannels = samplePoints.get(samplePoints.size() - 1);
      final List<Channel<String>> channelList = new ArrayList<>();
      final AtomicInteger connectionCount = new AtomicInteger(0);


      logger.fine( String.format( "Creating and asynchronously connecting %d channels...", maxChannels ) );
      for ( int i = 0; i < maxChannels; i++ )
      {
         try
         {
            final Channel<String> caChannel = context.createChannel("ca:test:counter01", String.class);
            channelList.add(caChannel);
            caChannel.addConnectionListener(( c, b ) -> {
               if ( b )
               {
                  connectionCount.incrementAndGet();
               }
               else
               {
                  logger.warning( "ConnectionListener indicated unexpected disconnect." );
               }
            });
            caChannel.connectAsync();
         }
         catch ( Throwable ex )
         {
            logger.warning( String.format( "Test terminated due to exception after creating and asynchronously connecting %d channels", i ) );
            return;
         }
      }
      logger.fine( String.format( "%d channels created.", maxChannels ) );

      while ( connectionCount.get() < maxChannels )
      {
         logger.fine(  String.format( "Waiting for completion %d / %d ", connectionCount.get(), maxChannels) );
         Thread.sleep(1000);
      }
      logger.fine( String.format( "%d channels connected.", maxChannels ) );
      logger.fine( "Performing asynchronous Get on all channels..." );

      final AtomicInteger getCompletionCount = new AtomicInteger(0);
      final StopWatch stopWatch = StopWatch.createStarted();
      final Map<Integer, Long> resultMap = new LinkedHashMap<>();

      for ( int i = 0; i < maxChannels; i++ )
      {
         try
         {
            final Channel<String> channel = channelList.get(i);
            channel.getAsync()
                  .thenAccept(( v ) -> {
                     final int count = getCompletionCount.incrementAndGet();
                     if ( samplePoints.contains(count) )
                     {
                        resultMap.put(count, stopWatch.getTime());
                        logger.fine( String.format( "Asynchronous Get completed normally on %d channels. Last value was: %s", count, v ) );
                     }
                  });

            if ( samplePoints.contains(i) )
            {
               logger.fine( String.format( "Asynchronous Get requested on %d channels", i ) );
            }
         }
         catch ( Throwable ex )
         {
            logger.fine( String.format( "Test terminated due to exception after getting from %d channels", i ) );
            break;
         }
      }
      logger.fine( "Asynchronous Get was requested on all channels." );

      while ( getCompletionCount.get() < maxChannels )
      {
         logger.fine( String.format( "Waiting for completion %d / %d ", getCompletionCount.get(), maxChannels ) );
         Thread.sleep(1000 );
      }
      logger.fine( String.format( "%d channels delivered their get results.", maxChannels ) );

      logger.info( "RESULTS:" );
      logger.info( "Q18: What is the cost of performing an asynchronous get on multiple channels (same PV) ? Answer: **See below.**" );
      logger.info("```");
      for ( int result : resultMap.keySet() )
      {
         logger.info( String.format( "- Asynchronous Get from %d channels took %d ms. Average: %.3f ms", result, resultMap.get( result ), (float) resultMap.get( result ) / result ) );
      }
      logger.info("```");
   }

   /**
    * Q19: What is the cost of performing an asynchronous get on multiple channels (different PV's) ?
    */
   @Test
   void q19() throws InterruptedException
   {
      logger.fine( "Performing Q19 Test: please wait..." );
      final List<Integer> samplePoints = Arrays.asList( 1, 10, 100, 1_000, 10_000, 20_000, 40_000, 60_000, 80_000, 100_000 );
      final int maxChannels = samplePoints.get(samplePoints.size() - 1);
      final List<Channel<String>> channelList = new ArrayList<>();

      final AtomicInteger connectionCount = new AtomicInteger(0);

      logger.fine( String.format( "Creating and asynchronously connecting %d channels...", maxChannels ) );
      for ( int i = 0; i < maxChannels; i++ )
      {
         try
         {
            final Channel<String> caChannel = context.createChannel("ca:test:counter" + String.format("%02d", (i % 100)), String.class);
            channelList.add(caChannel);
            caChannel.addConnectionListener(( c, b ) -> connectionCount.incrementAndGet());
            caChannel.connectAsync();
         }
         catch ( Throwable ex )
         {
            logger.fine( "Test terminated due to exception after creating and asynchronously connecting %d channels" + i );
            return;
         }
      }
      logger.fine( String.format( "%d channels created.", maxChannels ) );

      while ( connectionCount.get() < maxChannels )
      {
         Thread.sleep(100 );
      }
      logger.fine( String.format( "%d channels connected.", maxChannels ) );
      logger.fine( "Performing asynchronous get on all channels..." );

      Thread.sleep(1000 );
      final AtomicInteger getCompletionCount = new AtomicInteger(0);
      final StopWatch stopWatch = StopWatch.createStarted();
      final Map<Integer, Long> resultMap = new LinkedHashMap<>();
      for ( int i = 0; i < maxChannels; i++ )
      {
         try
         {
            final Channel<String> channel = channelList.get( i );
            if ( channel.getConnectionState() != ConnectionState.CONNECTED )
            {
               logger.warning( "OOOOOOPPPS!!!!" );
            }
            channel.getAsync()
                  .thenAccept(( v ) -> {
                     final int count = getCompletionCount.incrementAndGet();
                     if ( samplePoints.contains( count) )
                     {
                        resultMap.put( count, stopWatch.getTime() );
                        logger.fine( String.format( "Asynchronous Get completed on %d channels. Last value was: %s", count, v ) );
                     }
                  });

            if ( samplePoints.contains( i ) )
            {
               logger.fine( String.format( "Asynchronous Get requested on %d channels", i ) );
            }
         }
         catch ( Throwable ex )
         {
            logger.fine( String.format( "Test terminated due to exception after getting from %d channels", i ) ) ;
            break;
         }
      }
      logger.fine( "Asynchronous get was requested on all channels." );

      while ( getCompletionCount.get() < maxChannels )
      {
         Thread.sleep(100);
      }
      logger.fine( String.format( "%d channels delivered their get results.", maxChannels ) );

      logger.info( "RESULTS:" );
      logger.info( "Q19: What is the cost of performing an asynchronous get on multiple channels (different PVs) ? Answer: **See below.**" );
      logger.info( "```" );
      for ( int result : resultMap.keySet() )
      {
         logger.info( String.format( "- Asynchronous Get from %d channels took %d ms. Average: %.3f ms", result, resultMap.get( result ), (float) resultMap.get( result ) / result ) );
      }
      logger.info("```");
   }

   /**
    * Q20: What is the cost of performing a monitor on multiple channels ?
    */
   @ParameterizedTest
   @ValueSource( strings = { "BlockingQueueSingleWorkerMonitorNotificationServiceImpl",
                             "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl",
                             "StripedExecutorServiceMonitorNotificationServiceImpl"} )

   void q20( String serviceImpl ) throws InterruptedException
   {
      logger.fine( "Performing Q20 Test using Monitor Notification Service Impl {}: please wait..." + serviceImpl );

      System.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );
      final Context mySpecialContext = new Context();

      final List<Integer> samplePoints = Arrays.asList( 1, 100, 200, 500, 1000 );
      final int maxChannels = samplePoints.get(samplePoints.size() - 1);

      final List<Channel<String>> channelList = new ArrayList<>();
      final AtomicInteger connectionCount = new AtomicInteger(0);
      logger.fine( String.format( "Creating and asynchronously connecting %d channels...", maxChannels ) );
      for ( int i = 0; i < maxChannels; i++ )
      {
         try
         {
            final Channel<String> caChannel = mySpecialContext.createChannel("ca:test:counter01", String.class);
            channelList.add(caChannel);
            caChannel.addConnectionListener(( c, b ) -> {
               //logger.info("Channel: {} state: {}", c, b);
               connectionCount.incrementAndGet();
            });
            caChannel.connectAsync();
         }
         catch ( Throwable ex )
         {
            logger.warning( String.format( "Test terminated due to exception after creating and asynchronously connecting %d channels", i ) );
            return;
         }
      }

      logger.fine( String.format( "%d channels created.", maxChannels ) );

      while ( connectionCount.get() < maxChannels )
      {
         Thread.sleep(100);
      }
      logger.fine( String.format( "%d channels connected.", maxChannels ) );

      logger.fine( "Performing addValueMonitor on all channels..." );
      final AtomicInteger monitorUpdateCounter = new AtomicInteger(0);
      final StopWatch stopWatch = StopWatch.createStarted();
      final Map<Integer, Long> resultMap = new LinkedHashMap<>();
      final Map<Channel<String>, String> monitorMap = Collections.synchronizedMap(new LinkedHashMap<>());
      for ( int i = 0; i < maxChannels; i++ )
      {
         try
         {
            final Channel<String> channel = channelList.get(i);
            channel.addValueMonitor(( v ) -> {
               final int count = monitorUpdateCounter.incrementAndGet();
               //logger.info("Monitor update on channel: {}, had value: {} ", channel, v);
               monitorMap.put(channel, v);
               //logger.info("Number of unique channels is: {}", monitorMap.keySet().size());
               if ( samplePoints.contains(count) )
               {
                  resultMap.put(count, stopWatch.getTime());
                  logger.fine( String.format( "Monitor was established on %d channels. Observed value for channel %s was: %s", count, channel, v ) );
               }
            });

            if ( samplePoints.contains(i) )
            {
               logger.fine( String.format( "Monitor requested on %d channels", i ) );
            }
         }
         catch ( Throwable ex )
         {
            logger.warning( String.format( "Test terminated due to exception after adding value monitor to %d channels", i ) );
            return;
         }
      }
      logger.fine("Monitor was requested on all channels.");

      while ( monitorUpdateCounter.get() < maxChannels )
      {
         Thread.sleep(100);
      }
      logger.fine( String.format( "%d channels delivered their addValueMonitor results.", maxChannels ) );

      logger.info( "RESULTS:" );
      logger.info( "Implementation: " + serviceImpl );
      logger.info( "Q20: What is the cost of performing a monitor on multiple channels ? Answer: **See below.**" );
      logger.info( "```" );
      for ( int result : resultMap.keySet() )
      {
         logger.info( String.format( "- Asynchronous Monitor from %d channels took %d ms. Average: %.3f ms", result, resultMap.get( result ), (float) resultMap.get( result ) / result ) );
      }
      logger.info( "```" );
   }

   /**
    * Q21: What is the cost/performance when using CA to transfer large arrays ?
    */
   @ParameterizedTest
   @ValueSource( strings = { "BlockingQueueSingleWorkerMonitorNotificationServiceImpl",
                             "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl",
                             "StripedExecutorServiceMonitorNotificationServiceImpl"} )

   void q21( String serviceImpl )
   {
      logger.fine("Performing Q21 Test: please wait...");

      System.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );

      final int MAX_ELEMENTS_TO_TRANSFER = 10_000_000;
      final int SIZE_OF_ELEMENT_IN_BYTES = Integer.SIZE / Byte.SIZE;
      final List<Integer> elementsToTransferSamplingPoints = Arrays.asList( 10_000, 20_000, 50_000,
                                                                            100_000, 200_000, 500_000,
                                                                            1_000_000, 2_000_000, 5_000_000, 10_000_000);

      // One could consider setting up a context here which allocates enough room for the data to be transferred.
      // But the CA library claims that it is ok to leave the value undefined so we don't do this. It IS important
      // to enable large data transfer on the test SoftIOC though. Before running the IOC the following definition
      // should be specified: export EPICS_CA_MAX_ARRAY_BYTES=1000000000
      final Map<Integer, Long> resultMap = new LinkedHashMap<>();
      try ( final Context ctx = new Context() )
      {
         logger.fine("Creating channel...");
         final Channel<int[]> caChannel = ctx.createChannel("ca:test:rawdata", int[].class);

         logger.fine("Connecting channel...");
         caChannel.connect();

         for ( int elementsToTransfer : elementsToTransferSamplingPoints )
         {
            logger.fine( String.format( "Measuring transfer time for array of size %d elements...", elementsToTransfer) );

            // Set up an array of the appropriate transfer size
            final int[] arr = new int[ elementsToTransfer ];
            for ( int i = 0; i < elementsToTransfer; i++ )
            {
               arr[ i ] = i;
            }

            // By performing a caput of an array with the required size this will ensure that
            // the EPICS waveform .NORD field gets set correctly. This will ensure that the
            // full data set gets transferred when performing measurements on the subsequent
            // get transfer rates.
            caChannel.put( arr );

            final StopWatch totalElapsedTimeStopWatch = StopWatch.createStarted();
            caChannel.get();
            long elapsedTime = totalElapsedTimeStopWatch.getTime();
            resultMap.put(elementsToTransfer, elapsedTime);
         }
      }
      catch ( Throwable ex )
      {
         logger.warning( String.format( "Test terminated unexpectedly due to exception: '%s'", ex.toString() ) );
         return;
      }

      logger.info( "RESULTS:" );
      logger.info( "Implementation: " + serviceImpl );
      logger.info( "Q21: What is the cost/performance when using CA to transfer large arrays ? Answer: **See below.**" );
      logger.info( "```" );
      for ( int result : resultMap.keySet() )
      {
         final float transferRate = ((float) 1000 * SIZE_OF_ELEMENT_IN_BYTES * result) / (((float) resultMap.get(result)) * 1024 * 1024);
         logger.info( String.format( "- Transfer time for integer array of %d elements took %d ms. Transfer rate: %.3f MB/s", result, resultMap.get(result), transferRate ) ) ;
      }
      logger.info( "```" );
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
