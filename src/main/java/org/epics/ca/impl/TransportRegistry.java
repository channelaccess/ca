package org.epics.ca.impl;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.epics.ca.util.IntHashMap;

/**
 * Class to cache CA transports (connections to other hosts).
 */
public class TransportRegistry
{

   /**
    * Map caching transports.
    */
   private final Map<InetSocketAddress, IntHashMap<Transport>> transports = new HashMap<> ();

   /**
    * Array of all transports.
    */
   private final ArrayList<Transport> allTransports = new ArrayList<> ();

   /**
    * Cache new (address, transport) pair.
    *
    * @param address   address of the host computer.
    * @param transport tranport to the host computer.
    */
   public void put( InetSocketAddress address, Transport transport )
   {
      synchronized ( transports )
      {
         IntHashMap<Transport> priorities = transports.get( address);
         if ( priorities == null )
         {
            priorities = new IntHashMap<>();
            transports.put( address, priorities);
         }
         priorities.put (transport.getPriority (), transport);
         allTransports.add (transport);
      }
   }

   /**
    * Lookup for a transport for given address.
    *
    * @param address  address of the host computer.
    * @param priority priority of the transport.
    * @return corresponding transport, <code>null</code> if none found.
    */
   public Transport get( InetSocketAddress address, int priority )
   {
      synchronized ( transports )
      {
         IntHashMap<Transport> priorities = transports.get (address);
         if ( priorities != null )
            return priorities.get (priority);
         else
            return null;
      }
   }

   /**
    * Lookup for a transport for given address (all priorities).
    *
    * @param address address of the host computer.
    * @return array of corresponding transports, <code>null</code> if none found.
    */
   public Transport[] get( InetSocketAddress address )
   {
      synchronized ( transports )
      {
         IntHashMap<Transport> priorities = transports.get (address);
         if ( priorities != null )
         {
            Transport[] ts = new Transport[ priorities.size () ];
            priorities.toArray (ts);
            return ts;
         }
         else
            return null;
      }
   }

   /**
    * Remove (address, transport) pair from cache.
    *
    * @param address  address of the host computer.
    * @param priority priority of the transport to be removed.
    * @return removed transport, <code>null</code> if none found.
    */
   public Transport remove( InetSocketAddress address, int priority )
   {
      synchronized ( transports )
      {
         IntHashMap<Transport> priorities = transports.get (address);
         if ( priorities != null )
         {
            Transport transport = priorities.remove (priority);
            if ( priorities.size () == 0 )
               transports.remove (address);
            if ( transport != null )
               allTransports.remove (transport);
            return transport;
         }
         else
            return null;
      }
   }

   /**
    * Clear cache.
    */
   public void clear()
   {
      synchronized ( transports )
      {
         transports.clear ();
         allTransports.clear ();
      }
   }

   /**
    * Get number of active (cached) transports.
    *
    * @return number of active (cached) transports.
    */
   public int numberOfActiveTransports()
   {
      synchronized ( transports )
      {
         return allTransports.size ();
      }
   }

   /**
    * Get array of all active (cached) transports.
    *
    * @return array of all active (cached) transports.
    */
   public Transport[] toArray()
   {
      synchronized ( transports )
      {
         return allTransports.toArray (new Transport[ transports.size () ]);
      }
   }
}
