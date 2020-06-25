/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import com.cosylab.epics.caj.cas.util.examples.CounterProcessVariable;
import gov.aps.jca.CAException;
import gov.aps.jca.JCALibrary;
import gov.aps.jca.cas.ServerContext;
import gov.aps.jca.dbr.DBR_Double;
import gov.aps.jca.dbr.DBR_Enum;
import gov.aps.jca.dbr.DBR_Int;

import com.cosylab.epics.caj.cas.util.DefaultServerImpl;
import com.cosylab.epics.caj.cas.util.MemoryProcessVariable;
import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.JavaProcessManager;
import org.epics.ca.util.logging.LibraryLogManager;

import java.io.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

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

   private static final Logger logger = LibraryLogManager.getLogger( EpicsChannelAccessTestServer.class );
   private static final AtomicReference<JavaProcessManager> processManagerRef = new AtomicReference<>();
   private final ServerContext context;


/*- Main ---------------------------------------------------------------------*/

   /**
    * Runs the EPICS CA Test Server from the command line.
    *
    * @param args command-line arguments (not used).
    */
   public static void main( String[] args )
   {
      logger.info( "The EPICS Channel Access Test Server is starting..." );

      final EpicsChannelAccessTestServer epicsChannelAccessTestServer;
      try
      {
         epicsChannelAccessTestServer = new EpicsChannelAccessTestServer();
      }
      catch ( CAException ex )
      {
         final String msg = "An exception occurred which prevented the server from starting.";
         logger.log( Level.WARNING, msg, ex );
         throw new RuntimeException( "An exception occurred which prevented the server from starting." );
      }

      // Display basic information about the CA context associated with the server.
      epicsChannelAccessTestServer.printContextInfo();

      // Start the server in a separate daemon thread that will continue until shut down.
      epicsChannelAccessTestServer.run();

      // Return the reference to the server (which will allow it to be shutdown when required).
      logger.info( "The EPICS Channel Access Test Server was initialised and is running.\n" );
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
      System.setProperty( "EPICS_CA_ADDR_LIST", "localhost" );

      // Get the JCALibrary instance.
      final JCALibrary jca = JCALibrary.getInstance();

      // Create a context based on the library default server implementation
      // CAException -->
      final DefaultServerImpl server = new DefaultServerImpl();
      context = jca.createServerContext( JCALibrary.CHANNEL_ACCESS_SERVER_JAVA, server );

      // Register the process variables required for any subsequent tests.
      registerProcessVariables( server );
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * Starts the EpicsChannelAccessTestServer in a separate process.
    *
    * The test server should not already have been started.
    *
    * @throws IllegalStateException if the test server had already been started.
    */
   public static void start()
   {
      Validate.validState( ! isStarted(),"The EpicsChanneAccessTestServer was not shutdown." );

      final Properties properties = new Properties();
      properties.setProperty( "com.cosylab.epics.caj.cas.CAJServerContext.max_array_bytes", String.valueOf( 4 * 1024 * 1024 + 1024 + 32 ) );
      //properties.setProperty( "com.cosylab.epics.caj.cas.CAJServerContext.beacon_addr_list", "127.0.0.1" );
      //properties.setProperty( "com.cosylab.epics.caj.cas.CAJServerContext.auto_beacon_addr_list", "false" );
      final String[] noProgramArgs = new String[] {};
      final JavaProcessManager processManager = new JavaProcessManager( EpicsChannelAccessTestServer.class, properties, noProgramArgs );
      processManager.start(true );
      processManagerRef.set( processManager );
   }

   /**
    * Shuts down an EpicsChannelAccessTestServer instance that was previously started.
    *
    * The test server should have been started via a previous call to the <code>start</code>
    * method.
    *
    * @throws IllegalStateException if the test server had NOT already been started.
    */
   public static void shutdown()
   {
      Validate.validState( isStarted(),"The EpicsChanneAccessTestServer was not started." );

      processManagerRef.get().shutdown();
      processManagerRef.set( null );
   }

/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/

   /**
    * Returns indication of whether the <code>start</code> method was previously
    * called.
    *
    * @return the result
    */
   private static boolean isStarted()
   {
      return processManagerRef.get() != null;
   }

   /**
    * Runs the EPICS CA Test Server from a separate thread in the calling
    * JVM instance, returning immediately.
    *
    * The test server runs indefinitely until the <code>destroy</code>
    * method is called or until unless some unexpected exception condition
    * terminates processing prematurely.
    */
   void run()
   {
      // Run the server indefinitely or until some exception occurs.
      try
      {
         // Zero means run and block forever.
         context.run( 0 );
      }
      catch( CAException ex )
      {
         final String msg = "The following unexpected exception occurred:" ;
         logger.log( Level.WARNING, msg, ex );
         throw new RuntimeException( msg, ex );
      }
      logger.info( "Done" );
   }

   private void printContextInfo()
   {
      // Log the version
      logger.info( context.getVersion().getVersionString() );

      // The following rigmarole is to capture the context information
      // output from the library and to send it through the logger,
      // respecting the line breaks.
      final ByteArrayOutputStream os = new ByteArrayOutputStream();
      final PrintStream printStream = new PrintStream( os, true );
      context.printInfo( printStream );
      printStream.close();

      try ( final Reader reader = new InputStreamReader( new ByteArrayInputStream( os.toByteArray() ) );
            final BufferedReader bufferedReader = new BufferedReader( reader )  )
      {
         String line;
         while ( (line = bufferedReader.readLine()) != null )
         {
            logger.info(line);
         }
      }
      catch ( IOException ex )
      {
         logger.log( Level.WARNING, "Exception reading stream", ex );
      }
   }

   /**
    * Destroys the test server context, releasing all resources.
    */
   private void destroyContextWithoutPropagatingExceptions()
   {
      try
      {
         logger.finer( "Destroying context." );
         context.destroy();
         logger.finer( "Context was destroyed." );
      }
      catch ( IllegalStateException | CAException ex )
      {
         logger.log( Level.WARNING, "Context destroy operation raised exception. Will interrupt calling thread.", ex );
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
      // Note: the variables below are typically created with two elements per channel. This means
      // they can be tested on the client side by connecting using either as scalars or as arrays.

      // Simple in-memory PV
      // "simple"
      server.createMemoryProcessVariable ("simple", DBR_Int.TYPE, new int[] { 1, 2, 3 });

      // PV supporting all GR/CTRL info
      // "adc01"
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

      // Create some other PV's needed for the PSI CA Client Library Example program
      // "adc02"
      final MemoryProcessVariable mpv2 = new MemoryProcessVariable ("adc02", null, DBR_Double.TYPE, new double[] { 1.04, 33.31 });
      server.registerProcessVaribale( mpv2 );

      // "adc03"
      final MemoryProcessVariable mpv3 = new MemoryProcessVariable ("adc03", null, DBR_Double.TYPE, new double[] { 19.78, 53.11 });
      server.registerProcessVaribale( mpv3 );

      // "adc04"
      final MemoryProcessVariable mpv4 = new MemoryProcessVariable ("adc04", null, DBR_Double.TYPE, new double[] { 19.78, 53.11 });
      server.registerProcessVaribale( mpv4 );

      // enum in-memory PV
      // "enum"
      final MemoryProcessVariable enumPV = new MemoryProcessVariable ("enum", null, DBR_Enum.TYPE, new short[] { 3, 1 } ) {
         private final String[] labels = { "zero", "one", "two", "three", "four", "five", "six", "seven" };

         /* (non-Javadoc)
          * @see com.cosylab.epics.caj.cas.util.MemoryProcessVariable#getEnumLabels()
          */
         public String[] getEnumLabels()
         {
            return labels;
         }
      };
      server.registerProcessVaribale( enumPV );

      // counter PV
      // "100msCounter"
      final CounterProcessVariable counter = new CounterProcessVariable("100msCounter", null, -10, 10, 1, 100, -7, 7, -9, 9);
      server.registerProcessVaribale( counter );

      // fast counter PV
      // "1msCounter"
      final CounterProcessVariable fastCounter = new CounterProcessVariable("1msCounter", null, Integer.MIN_VALUE, Integer.MAX_VALUE, 1, 1, -7, 7, -9, 9);
      server.registerProcessVaribale( fastCounter );

      // simple in-memory 1MB array
      // "large"
      final int[] arrayValue = new int[ 1024 * 1024 ];
      for ( int i = 0; i < arrayValue.length; i++ )
      {
         arrayValue[ i ] = i;
      }
      server.createMemoryProcessVariable ("large", DBR_Int.TYPE, arrayValue);
   }

/*- Nested Classes -----------------------------------------------------------*/

}
