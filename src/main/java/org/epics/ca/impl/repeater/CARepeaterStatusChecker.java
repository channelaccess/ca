/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.time.StopWatch;
import org.epics.ca.util.logging.LibraryLogManager;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Provides a service for handling requests to start or shutdown CA Repeater
 * instances on multiple ports.
 */
public class CARepeaterStatusChecker
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final int PROCESS_STABILISATION_DELAY_IN_MILLIS= 2000;
   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterStatusChecker.class );

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

  /**
    * Makes an instantaneous check to determine whether a CA Repeater instance
   * is running on the specified port on any of the addresses bound to the
   * local machine.
    *
    * This method does not block.
    *
    * @param repeaterPort the port on which the CA Repeater run state is to be tested.
    * @return <code>true</code> if repeater is already running, <code>false</code> otherwise
    */
   public static boolean isRepeaterRunning( int repeaterPort )
   {
      logger.finest( "Checking whether repeater is running on port " + repeaterPort );

      // The idea here is that binding to a port on the wildcard address we check all
      // possible network interfaces on which the CA Repeater may be already running.
      final InetSocketAddress wildcardSocketAddress = new InetSocketAddress( repeaterPort );
      logger.finest( "Checking socket address " + wildcardSocketAddress );
      final boolean repeaterIsRunning = ! UdpSocketUtilities.isSocketAvailable( wildcardSocketAddress );
      final String runningOrNot = repeaterIsRunning ? " IS " : " IS NOT ";
      logger.finest( "The repeater on port " + repeaterPort + runningOrNot + "running." );
      return repeaterIsRunning;
   }

   /**
    * Verifies that the CA Repeater instance starts within a reasonable timescale
    * when allowing for process startup delays.
    *
    * @param repeaterPort the port on which the CA Repeater run state is to be tested.
    * @return the result.
    */
   public static boolean verifyRepeaterStarts( int repeaterPort )
   {
      return isRepeaterInExpectedState( repeaterPort, true );
   }

   /**
    * Verifies that the CA Repeater instance stops within a reasonable timescale
    * when allowing for process shutdown delays.
    *
    * @param repeaterPort the port on which the CA Repeater run state is to be tested.
    * @return the result.
    */
   public static boolean verifyRepeaterStops( int repeaterPort )
   {
      return isRepeaterInExpectedState( repeaterPort, false );
   }

/*- Package-level access methods ---------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/

   /**
    * Verifies that the CA Repeater instance on the specified port is in the expected state,
    * if necessary waiting up to <code>PROCESS_STABILISATION_DELAY_IN_MILLIS</code> for
    * the repeater run process to stabilise.
    *
    * @param repeaterPort the port on which the CA Repeater run state is to be tested.
    * @param expectedRunState the expected running state for the CA Repeater.
    * @return true when the expected state was verified.
    */
   private static boolean isRepeaterInExpectedState( int repeaterPort, boolean expectedRunState )
   {
      final int periodicPollIntervalInMilliseconds = 10;
      final StopWatch stopWatch = StopWatch.createStarted();
      while ( ( isRepeaterRunning( repeaterPort ) != expectedRunState ) && ( stopWatch.getTime() < PROCESS_STABILISATION_DELAY_IN_MILLIS ) )
      {
         logger.finest( "Waiting 10ms..." );
         try
         {
            //noinspection BusyWait
            Thread.sleep( periodicPollIntervalInMilliseconds );
         }
         catch ( InterruptedException ex )
         {
            logger.warning( "Interrupted from sleep !" );
            Thread.currentThread().interrupt();
         }
      }
      stopWatch.stop();
      final boolean verified = stopWatch.getTime() < PROCESS_STABILISATION_DELAY_IN_MILLIS;
      if ( ! verified )
      {
         logger.warning( "Timeout whilst waiting for CA Repeater to reach expected state !" );
      }
      return verified;
   }

/*- Nested Classes -----------------------------------------------------------*/

}
