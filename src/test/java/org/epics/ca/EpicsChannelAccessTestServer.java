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

import java.util.concurrent.Executors;
import java.util.concurrent.Future;


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

   /**
    * JCA server context.
    */
   private volatile ServerContext context = null;

/*- Main ---------------------------------------------------------------------*/

   /**
    * Program entry point.
    *
    * @param args command-line arguments
    */
   public static void main( String[] args )
   {
      // execute
      new EpicsChannelAccessTestServer().execute ();
   }

/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   public Future<?> runInSeparateThread()
   {
      final Future<?> myFuture;
      try
      {
         // initialize context
         initialize ();

         // Run server
         myFuture = Executors.newSingleThreadExecutor().submit( () -> {
            try
            {
               context.run (0);
            }
            catch ( Throwable th )
            {
               th.printStackTrace ();
            }
         } );
      }
      catch ( Throwable th )
      {
         throw new RuntimeException( "Failed to start CA server.", th );
      }
      return myFuture;
   }

   /**
    * Destroy JCA server context.
    */
   public void destroy()
   {
      try
      {
         // Destroy the context, check if never initialized.
         if ( context != null )
         {
            context.destroy();
         }
      }
      catch ( Throwable th )
      {
         th.printStackTrace ();
      }
   }


/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/

   /**
    * Initialize JCA context.
    *
    * @throws CAException throws on any failure.
    */
   private void initialize() throws CAException
   {

      // Get the JCALibrary instance.
      final JCALibrary jca = JCALibrary.getInstance ();

      // Create server implementation
      final DefaultServerImpl server = new DefaultServerImpl ();

      // Create a context with default configuration values.
      System.setProperty( "EPICS_CA_ADDR_LIST", "localhost" );
      context = jca.createServerContext( JCALibrary.CHANNEL_ACCESS_SERVER_JAVA, server );

      // register process variables
      registerProcessVariables( server );
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
         private final String[] labels =
               { "zero", "one", "two", "three", "four", "five", "six", "seven" };

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

   private void execute()
   {
      try
      {
         // initialize context
         initialize ();

         // Display basic information about the context.
         System.out.println (context.getVersion ().getVersionString ());
         context.printInfo ();
         System.out.println ();

         System.out.println ("Running server...");

         // run server
         context.run (0);

         System.out.println ("Done.");

      }
      catch ( Throwable th )
      {
         th.printStackTrace ();
      }
      finally
      {
         // always finalize
         destroy ();
      }
   }


/*- Nested Classes -----------------------------------------------------------*/

}
