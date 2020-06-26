/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.util.logging.LibraryLogManager;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Provides a service for handling requests to start or shutdown CA Repeater
 * instances on multiple ports.
 */
public class CARepeaterServiceManager
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterServiceManager.class );
   private static final File NULL_FILE = new File( (System.getProperty( "os.name" ).startsWith( "Windows" ) ? "NUL" : "/dev/null") );

   /**
    * Map of service instances (= CA Repeaters running on particular port) and
    * the number of times the services has been requested or cancelled.
    */
   private static final Map<CARepeaterServiceInstance, Integer> serviceInterestMap = new HashMap<>();

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   /**
    * Handles a request for the CA Repeater Service on the specified port.
    * <p>
    * The request for CA Repeater services does not always result in an instance
    * of the CA Repeater from being started since various conditions may
    * prevent it. These situations are considered normal and are logged
    * but do not result in any exceptions being raised.
    * <p>
    * Things that are handled as part of the normal processing:
    * <ol>
    *    <li>The library configuration has disabled the service.</li>
    *    <li>There have been previous requests for the CA Repeater Service on this
    *    port which have not yet been cancelled.</li>
    *    <li>An instance of the CA Repeater was already started by someone else
    *    on the local machine.</li>
    *    <li>The attempt to start a new CA Repeater service instance results
    *    in an unexpected error.</li>
    * </ol>
    *
    * @param repeaterPort the port on which the service is requested.
    */
   public static void requestServiceOnPort( int repeaterPort )
   {
      synchronized ( CARepeaterServiceManager.class )
      {
         logger.fine( "Processing request for CA Repeater Services on port '" + repeaterPort + "." );

         final int currentInterestLevel = getServiceInterestLevelForPort( repeaterPort );
         if ( getServiceInterestLevelForPort( repeaterPort ) == 0 )
         {
            logger.fine( "Starting the port " + repeaterPort + " CA Repeater Service instance." );
            final CARepeaterServiceInstance serviceInstance = new CARepeaterServiceInstance( repeaterPort );
            serviceInterestMap.put( serviceInstance, 1 );
            serviceInstance.activate();
         }
         else
         {
            logger.finer( "Increasing the interest level in the port " + repeaterPort  + " CA Repeater Service instance." );
            increaseServiceInterestLevelForPort( repeaterPort );
         }
      }
   }

   /**
    * Cancels request for the CA Repeater Service on the specified port.
    * <p>
    * The request for a CA Repeater does not always result in an instance
    * of the CA Repeater from being shutdown since various conditions may
    * prevent it. These situations are considered normal and are logged
    * but do not result in any exceptions being raised.
    * <p>
    * Things that are handled as part of the normal processing:
    * <ol>
    *    <li>The library configuration has disabled the service.</li>
    *    <li>There were no previous service requests for the CA Repeater on
    *    this port.</li>
    *    <li>There were no previous service requests for the CA Repeater on
    *    this port.</li>
    * </ol>
    *
    * @param repeaterPort the port on which the service is requested.
    */
   public static void cancelServiceRequestOnPort( int repeaterPort )
   {
      synchronized ( CARepeaterServiceManager.class )
      {
         logger.fine( "Processing request for cancellation of CA Repeater Services on port: '" + repeaterPort + "'." );

         final int currentInterestLevel = getServiceInterestLevelForPort( repeaterPort );
         switch ( currentInterestLevel )
         {
            case 0:
               logger.warning( "The CA Repeater Service has received no previous service requests for port " + repeaterPort + "." );
               break;

            case 1:
               logger.fine( "Shutting down the port " + repeaterPort + " CA Repeater Service instance." );
               final Optional<CARepeaterServiceInstance> optInstance = getServiceInstanceFor( repeaterPort );
               Validate.validState( optInstance.isPresent() );
               serviceInterestMap.remove( optInstance.get() );
               optInstance.get().shutdown();
               break;

            default:
               logger.finer( "Reducing the interest level in the port " + repeaterPort  + " CA Repeater Service instance." );
               reduceServiceInterestlevelForPort( repeaterPort );
               break;
         }
      }
   }

   /**
    * Check if a repeater is running bound to any of the addresses associated
    * with the local machine.
    *
    * @param repeaterPort repeater port.
    * @return <code>true</code> if repeater is already running, <code>false</code> otherwise
    */
   public static boolean isRepeaterRunning( int repeaterPort )
   {
      logger.finest( "Checking whether repeater is running on port " + repeaterPort );

      // The idea here is that binding to a port on thewildcard addreess we check all
      // possible network interfaces on which the CA Repeater may be already running.
      final InetSocketAddress wildcardSocketAddress = new InetSocketAddress( repeaterPort );
      logger.finest( "Checking socket address " + wildcardSocketAddress );
      final boolean repeaterIsRunning = !UdpSocketUtilities.isSocketAvailable( wildcardSocketAddress );
      final String runningOrNot = repeaterIsRunning ? " IS " : " IS NOT ";
      logger.finest( "The repeater on port " + repeaterPort + runningOrNot + "running." );
      return repeaterIsRunning;
   }

/*- Package-level access methods ---------------------------------------------*/

   /**
    * This method is provided for test purposes only.
    *
    * @return the number of unique service instances that are currently active.
    */
   static int getServiceInstances()
   {
      return serviceInterestMap.size();
   }

/*- Private methods ----------------------------------------------------------*/

   /**
    * Returns the level of interest in a CA Repeater service interest
    * (= instance of a CA Repeater running on the specified port).
    *
    * The interest level is incremented every time a new service
    * request is made and decremented every time it is cancelled.
    *
    * @param port the port of interest.
    * @return the result.
    */
   private static int getServiceInterestLevelForPort( int port )
   {
      Validate.inclusiveBetween( 0, 65535, port );

      final Optional<Integer> currentInterestLevel = serviceInterestMap.entrySet().stream().filter( x -> x.getKey().getPort() == port ).map( Map.Entry::getValue ).findFirst();
      return currentInterestLevel.orElse( 0 );
   }

   /**
    * Returns the service instance, if any, associated with the specified port.
    *
    * @param port the port of interest.
    * @return the result.
    */
   private static Optional<CARepeaterServiceInstance> getServiceInstanceFor( int port )
   {
      Validate.inclusiveBetween( 0, 65535, port );
      return serviceInterestMap.keySet().stream().filter( integer -> integer.getPort() == port ).findFirst();
   }

   /**
    * Increases the level of interest in the service instance associated with
    * the specified port.
    *
    * @param port the target port.
    *
    * @throws IllegalArgumentException if the port is out of range.
    * @throws IllegalStateException if the service instance on this port was never requested.
    * @throws IllegalStateException if the current interest level wasn't at least 1.
    */
   private static void increaseServiceInterestLevelForPort( int port )
   {
      Validate.inclusiveBetween( 0, 65535, port );

      final Optional<CARepeaterServiceInstance> instance = getServiceInstanceFor( port );
      Validate.validState( instance.isPresent() );

      final int currentInterestLevel = getServiceInterestLevelForPort( port );
      Validate.validState( currentInterestLevel >= 1 );

      final int newInterestLevel = getServiceInterestLevelForPort( port ) + 1;
      serviceInterestMap.put( instance.get(), newInterestLevel );
   }

   /**
    * Reduces the level of interest in the service instance associated with
    * the specified port.
    *
    * @param port the target port.
    *
    * @throws IllegalArgumentException if the port is out of range.
    * @throws IllegalStateException if the service instance on this port was never requested.
    * @throws IllegalStateException if the current interest level wasn't at least 1.
    */

   private static void reduceServiceInterestlevelForPort( int port )
   {
      Validate.inclusiveBetween( 0, 65535, port );

      final Optional<CARepeaterServiceInstance> instance = getServiceInstanceFor( port );
      Validate.validState( instance.isPresent() );

      final int currentInterestLevel = getServiceInterestLevelForPort( port );
      Validate.validState( currentInterestLevel >= 1 );

      final int newInterestLevel = currentInterestLevel - 1;
      serviceInterestMap.put( instance.get(), newInterestLevel );
   }

/*- Nested Classes -----------------------------------------------------------*/

}
