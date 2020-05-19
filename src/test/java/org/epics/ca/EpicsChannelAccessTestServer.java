/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import gov.aps.jca.CAException;
import gov.aps.jca.JCALibrary;
import gov.aps.jca.cas.ServerContext;
import gov.aps.jca.dbr.DBR_Double;
import gov.aps.jca.dbr.DBR_Enum;
import gov.aps.jca.dbr.DBR_Int;

import com.cosylab.epics.caj.cas.util.DefaultServerImpl;
import com.cosylab.epics.caj.cas.util.MemoryProcessVariable;
import com.cosylab.epics.caj.cas.util.examples.CounterProcessVariable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Provides a test server to support integration testing of the PSI CA library.
 *
 * The current implementation uses the EPICS-community Java CA Server library.
 */
public class EpicsChannelAccessTestServer
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private final ExecutorService executor;
   private final ServerContext context;


/*- Main ---------------------------------------------------------------------*/

   /**
    * Runs the EPICS CA Test Server from the command line.
    *
    * @param args command-line arguments (not used).
    */
   public static void main( String[] args )
   {
      EpicsChannelAccessTestServer.start();
   }


/*- Constructor --------------------------------------------------------------*/

   /**
    * Creates a new instance.
    *
    * @throws CAException if some unexpected condition occurs.
    */
   private EpicsChannelAccessTestServer() throws CAException
   {
      // Create the library default server implementation
      // System.setProperty( "EPICS_CA_ADDR_LIST", "localhost" );

      // Get the JCALibrary instance.
      final JCALibrary jca = JCALibrary.getInstance();

      // Create a context based on the library default server implementation
      // CAException -->
      final DefaultServerImpl server = new DefaultServerImpl();
      context = jca.createServerContext( JCALibrary.CHANNEL_ACCESS_SERVER_JAVA, server );

      // Register the process variables required for any subsequent tests.
      registerProcessVariables( server );

      executor = Executors.newSingleThreadExecutor();
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * Starts the EPICS CA Test Server and returns a reference which will
    * allow it to be shut down in the future.
    *
    * @return the server reference.
    * @throws RuntimeException if anything prevents the server from starting.
    */
   public static EpicsChannelAccessTestServer start()
   {
      System.out.println( "\nStarting the EPICS Channel Access Test Server..." );

      final EpicsChannelAccessTestServer server;
      try
      {
          server = new EpicsChannelAccessTestServer();
      }
      catch ( CAException ex )
      {
         throw new RuntimeException( "An exception occurred which prevented the server from starting." );
      }

      // Display basic information about the CA context associated with the server.
      server.printContextInfo();

      // Start the server in a separate daemon thread that will continue until shut down.
      server.startInSeparateThread();

      // Return the reference to the server (which will allow it to be shutdown when required).
      System.out.println( "The EPICS Channel Access Test Server was initialised and is running.\n" );
      return server;
   }

   /**
    * Shuts down the test server, releasing all resources.
    */
   public void shutdown()
   {
      System.out.println( "\nShutting down the EPICS Channel Access Test Server..." );
      executor.shutdownNow();
      destroyContextWithoutPropagatingExceptions();
      System.out.println( "The EPICS Channel Access Test Server was shutdown." );
   }


/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/

   /**
    * Runs the EPICS CA Test Server from a separate thread in the calling
    * JVM instance, returning immediately.
    *
    * The test server runs indefinitely until the <code>destroy</code>
    * method is called or until unless some unexpected exception condition
    * terminates processing prematurely.
    */
   private void startInSeparateThread()
   {
      executor.execute( () -> {

         // Run the server indefinitely or until some exception occurs.
         try
         {
            // Zero means run and block forever.
            context.run(0 );
         }
         catch( CAException ex )
         {
            ex.printStackTrace ();
            throw new RuntimeException( "The following unexpected exception occurred: '" + ex.getMessage() + "'", ex );
         }
         finally
         {
            destroyContextWithoutPropagatingExceptions();
         }
      } );
   }

   private void printContextInfo()
   {
      System.out.println( "TEST SERVER VERSION INFO:" );
      System.out.println( context.getVersion().getVersionString() );
      System.out.println( "TEST SERVER CONTEXT INFO:" );
      context.printInfo();

   }

   /**
    * Destroys the test server context, releasing all resources.
    */
   private void destroyContextWithoutPropagatingExceptions()
   {
      try
      {
         context.destroy();
      }
      catch ( IllegalStateException | CAException  ex )
      {
         Thread.currentThread().interrupt();
      }
   }

   /**
    * Register process variables.
    *
    * @param server the server.
    */
   private void registerProcessVariables( DefaultServerImpl server )
   {
      // Simple in-memory PV
      server.createMemoryProcessVariable ("simple", DBR_Int.TYPE, new int[] { 1, 2, 3 });

      // PV supporting all GR/CTRL info
      final MemoryProcessVariable mpv = new MemoryProcessVariable ("adc01", null, DBR_Double.TYPE, new double[] { 12.08, 3.11 });

      mpv.setUpperDispLimit( 10d );
      mpv.setLowerDispLimit( -10d );

      mpv.setUpperAlarmLimit( 9d );
      mpv.setLowerAlarmLimit( -9d );

      mpv.setUpperCtrlLimit( 8d );
      mpv.setLowerCtrlLimit( -8d );

      mpv.setUpperWarningLimit( 7d );
      mpv.setLowerWarningLimit( -7d );

      mpv.setUnits ("units");
      mpv.setPrecision ((short) 3);
      server.registerProcessVaribale (mpv);

      // Some other PV's used in the Example program
      final MemoryProcessVariable mpv2 = new MemoryProcessVariable ("adc02", null, DBR_Double.TYPE, new double[] { 1.04, 33.31 });
      server.registerProcessVaribale( mpv2 );
      final MemoryProcessVariable mpv3 = new MemoryProcessVariable ("adc03", null, DBR_Double.TYPE, new double[] { 19.78, 53.11 });
      server.registerProcessVaribale( mpv3 );
      final MemoryProcessVariable mpv4 = new MemoryProcessVariable ("adc04", null, DBR_Double.TYPE, new double[] { 19.78, 53.11 });
      server.registerProcessVaribale( mpv4 );

      // enum in-memory PV
      final MemoryProcessVariable enumPV = new MemoryProcessVariable ("enum", null, DBR_Enum.TYPE, new short[] { 0 })
      {
         private final String[] labels = { "zero", "one", "two", "three", "four", "five", "six", "seven" };

         /* (non-Javadoc)
          * @see com.cosylab.epics.caj.cas.util.MemoryProcessVariable#getEnumLabels()
          */
         public String[] getEnumLabels()
         {
            return labels;
         }

      };
      server.registerProcessVaribale (enumPV);

      // counter PV
      final CounterProcessVariable counter = new CounterProcessVariable ("counter", null, -10, 10, 1, 1000, -7, 7, -9, 9);
      server.registerProcessVaribale (counter);

      // fast counter PV
      final CounterProcessVariable fastCounter = new CounterProcessVariable("fastCounter", null, Integer.MIN_VALUE, Integer.MAX_VALUE, 1, 1, -7, 7, -9, 9);
      server.registerProcessVaribale(fastCounter);

      // simple in-memory 1MB array
      final int[] arrayValue = new int[ 1024 * 1024 ];
      for ( int i = 0; i < arrayValue.length; i++ )
      {
         arrayValue[ i ] = i;
      }
      server.createMemoryProcessVariable ("large", DBR_Int.TYPE, arrayValue);
   }

/*- Nested Classes -----------------------------------------------------------*/

}
