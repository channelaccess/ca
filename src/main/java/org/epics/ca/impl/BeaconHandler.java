/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl;

/*- Imported packages --------------------------------------------------------*/

import java.net.InetSocketAddress;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Beacon handler.
 */
public class BeaconHandler
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   /**
    * Context instance.
    */
   private final ContextImpl context;

   /*
    * Remote address for this handler.
    */
   //private final InetSocketAddress responseFrom;

   /**
    * Average period.
    */
   private long averagePeriod = Long.MIN_VALUE;

   /**
    * Period stabilization flag.
    * If beacon monitoring began when server is being (re)started,
    * beacon period increases by factor 2. This case is handled by this flag.
    */
   private boolean periodStabilized = false;

   /**
    * Last beacon sequence ID.
    */
   private long lastBeaconSequenceID;

   /**
    * Last beacon timestamp.
    */
   private long lastBeaconTimeStamp = Long.MIN_VALUE;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructor.
    *
    * @param context      the context.
    * @param responseFrom server to handle.
    */
   public BeaconHandler( ContextImpl context, InetSocketAddress responseFrom )
   {
      this.context = context;
      //this.responseFrom = responseFrom;
   }


/*- Public methods -----------------------------------------------------------*/

   /**
    * Update beacon period and do analytical checks (server re-started, routing problems, etc.)
    *
    * @param remoteTransportRevision the EPICS CA protocol revision number.
    * @param timestamp the timestamp.
    * @param sequentialID the ID.
    */
   public void beaconNotify( short remoteTransportRevision, long timestamp, long sequentialID )
   {
      final boolean networkChanged = updateBeaconPeriod( remoteTransportRevision, timestamp, sequentialID );

      //if ( networkChanged )
      //{
         //  what to do here?!!
         // report changedTransport
      //}
   }


/*- Private methods ----------------------------------------------------------*/

   /**
    * Update beacon period.
    *
    * @param remoteTransportRevision the EPICS CA protocol revision number.
    * @param timestamp the timestamp.
    * @param sequentialID the ID.
    * @return network change (server restarted) detected.
    */
   private synchronized boolean updateBeaconPeriod( short remoteTransportRevision, long timestamp, long sequentialID )
   {
      // first beacon notification check
      if ( lastBeaconTimeStamp == Long.MIN_VALUE )
      {
         // new server up...
         context.beaconAnomalyNotify();

         if ( remoteTransportRevision >= 10 )
         {
            lastBeaconSequenceID = sequentialID;
         }

         lastBeaconTimeStamp = timestamp;
         return false;
      }

      // v4.10+ support beacon sequential IDs and additional checks are possible:
      // - detect beacon duplications due to redundant routes
      // - detect lost beacons due to input queue overrun or damage
      if ( remoteTransportRevision >= 10 )
      {
         final long beaconSeqAdvance;
         if ( sequentialID >= lastBeaconSequenceID )
         {
            beaconSeqAdvance = sequentialID - lastBeaconSequenceID;
         }
         else
         {
            beaconSeqAdvance = (0x00000000FFFFFFFFL - lastBeaconSequenceID) + sequentialID;
         }

         lastBeaconSequenceID = sequentialID;

         // throw out sequence numbers just prior to, or the same as, the last one received
         // (this situation is probably caused by a temporary duplicate route )
         if ( beaconSeqAdvance == 0 || beaconSeqAdvance > 0x00000000FFFFFFFFL - 256 )
         {
            return false;
         }

         // throw out sequence numbers that jump forward by only a few numbers
         // (this situation is probably caused by a duplicate route
         //  or a beacon due to input queue overrun)
         if ( beaconSeqAdvance > 1 && beaconSeqAdvance < 4 )
         {
            return false;
         }
      }

      boolean networkChange = false;
      long currentPeriod = timestamp - lastBeaconTimeStamp;

      // second beacon, period can be calculated now
      if ( averagePeriod < 0 )
      {
         averagePeriod = currentPeriod;
      }
      else
      {
         // is this a server seen because of a restored network segment?
         if ( currentPeriod >= (averagePeriod * 1.25) )
         {
            if ( currentPeriod >= (averagePeriod * 3.25) )
            {
               context.beaconAnomalyNotify ();

               // trigger network change on any 3 contiguous missing beacons
               networkChange = true;
            }
            else if ( !periodStabilized )
            {
               // boost current period
               averagePeriod = currentPeriod;
            }
            else
            {
               // something might be wrong...
               context.beaconAnomalyNotify();
            }
         }
         // is this a server seen because of reboot
         // (beacons come at a higher rate just after the)
         else if ( currentPeriod <= (averagePeriod * 0.8) )
         {
            // server restarted...
            context.beaconAnomalyNotify();

            networkChange = true;
         }
         // all OK
         else
         {
            periodStabilized = true;
         }

         if ( networkChange )
         {
            // reset
            periodStabilized = false;
            averagePeriod = -1;
         }
         else
         {
            // update a running average period
            averagePeriod = (long) (currentPeriod * 0.125 + averagePeriod * 0.875);
         }
      }

      lastBeaconTimeStamp = timestamp;
      return networkChange;
   }


/*- Nested classes -----------------------------------------------------------*/

}
