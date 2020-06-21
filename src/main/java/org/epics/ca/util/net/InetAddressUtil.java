package org.epics.ca.util.net;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;

import java.util.List;
import java.util.StringTokenizer;

import org.epics.ca.Constants;

/**
 * <code>InetAddress</code> utility methods.
 */
public class InetAddressUtil
{

   /**
    * Get broadcast addresses.
    *
    * @param port port to be added to get socket address.
    * @return array of broadcast addresses with given port.
    */
   public static InetSocketAddress[] getBroadcastAddresses( int port )
   {
      Enumeration<NetworkInterface> nets;
      try
      {
         nets = NetworkInterface.getNetworkInterfaces();
      }
      catch ( SocketException se )
      {
         // fallback
         return new InetSocketAddress[] { new InetSocketAddress ("255.255.255.255", port) };
      }

      ArrayList<InetSocketAddress> list = new ArrayList<> (10);

      while ( nets.hasMoreElements () )
      {
         NetworkInterface net = nets.nextElement ();
         try
         {
            if ( net.isUp () )
            {
               final List<InterfaceAddress> interfaceAddresses = net.getInterfaceAddresses();
               if ( ! interfaceAddresses.isEmpty() )
               {
                  for ( InterfaceAddress addr : interfaceAddresses )
                  {
                     if ( addr.getBroadcast() != null )
                     {
                        InetSocketAddress isa = new InetSocketAddress(addr.getBroadcast(), port);
                        if ( !list.contains( isa ) )
                        {
                           list.add(isa);
                        }
                     }
                  }
               }
            }
         }
         catch ( Throwable th )
         {
            // some methods throw exceptions, some return null (and they shouldn't)
            // noop, skip that interface
         }
      }

      // fallback to loop
      if ( list.size () == 0 )
         list.add (new InetSocketAddress (InetAddress.getLoopbackAddress (), port));

      InetSocketAddress[] retVal = new InetSocketAddress[ list.size () ];
      list.toArray (retVal);
      return retVal;
   }

   /**
    * Convert an integer into an IPv4 INET address.
    *
    * @param addr integer representation of a given address.
    * @return IPv4 INET address.
    */
   public static InetAddress intToIPv4Address( int addr )
   {
      byte[] a = new byte[ 4 ];

      a[ 0 ] = (byte) ((addr >> 24) & 0xFF);
      a[ 1 ] = (byte) ((addr >> 16) & 0xFF);
      a[ 2 ] = (byte) ((addr >> 8) & 0xFF);
      a[ 3 ] = (byte) ((addr & 0xFF));

      InetAddress res = null;
      try
      {
         res = InetAddress.getByAddress (a);
      }
      catch ( UnknownHostException e )
      { /* noop */ }

      return res;
   }

   /**
    * Convert an IPv4 INET address to an integer.
    *
    * @param addr IPv4 INET address.
    * @return integer representation of a given address.
    * @throws IllegalArgumentException if the address is really an IPv6 address
    */
   public static int ipv4AddressToInt( InetAddress addr )
   {

      if ( addr instanceof Inet6Address )
         throw new IllegalArgumentException ("IPv6 address used in IPv4 context");

      byte[] a = addr.getAddress ();

      @SuppressWarnings( "UnnecessaryLocalVariable" )
      final int res = ((a[ 0 ] & 0xFF) << 24)
         | ((a[ 1 ] & 0xFF) << 16)
         | ((a[ 2 ] & 0xFF) << 8)
         | (a[ 3 ] & 0xFF);

      return res;
   }


   /**
    * Parse space delimited addresss[:port] string and return array of <code>InetSocketAddress</code>.
    *
    * @param list        space delimited addresss[:port] string.
    * @param defaultPort port take if not specified.
    * @return array of <code>InetSocketAddress</code>.
    */
   public static InetSocketAddress[] getSocketAddressList( String list, int defaultPort )
   {
      return getSocketAddressList (list, defaultPort, null);
   }

   /**
    * Parse space delimited address[:port] string and return array of <code>InetSocketAddress</code>.
    *
    * @param list        space delimited address[:port] string.
    * @param defaultPort port take if not specified.
    * @param appendList  list to be appended.
    * @return array of <code>InetSocketAddress</code>.
    */
   public static InetSocketAddress[] getSocketAddressList( String list, int defaultPort, InetSocketAddress[] appendList )
   {
      final ArrayList<InetSocketAddress> al = new ArrayList<> ();

      // parse string
      StringTokenizer st = new StringTokenizer (list);
      while ( st.hasMoreTokens () )
      {
         int port = defaultPort;
         String address = st.nextToken ();

         // check port
         int pos = address.indexOf (':');
         if ( pos >= 0 )
         {
            try
            {
               port = Integer.parseInt (address.substring (pos + 1));
            }
            catch ( NumberFormatException nfe )
            { /* noop */ }

            address = address.substring (0, pos);
         }

         // add parsed address
         al.add (new InetSocketAddress (address, port));
      }

      // copy to array
      int appendSize = (appendList == null) ? 0 : appendList.length;
      InetSocketAddress[] isar = new InetSocketAddress[ al.size () + appendSize ];
      al.toArray (isar);
      if ( appendSize > 0 )
      {
         System.arraycopy(appendList, 0, isar, al.size(), appendSize);
      }
      return isar;
   }

   private static String hostName = null;

   public static synchronized String getHostName()
   {
      if ( hostName == null )
      {
         // default fallback
         hostName = "localhost";

         try
         {
            InetAddress localAddress = InetAddress.getLocalHost ();
            hostName = localAddress.getHostName ();
         }
         catch ( Throwable uhe )
         {   // not only UnknownHostException
            // try with environment variable
            try
            {
               String envHN = System.getenv (Constants.CA_HOSTNAME_KEY);
               if ( envHN != null )
                  hostName = envHN;
            }
            catch ( Throwable th )
            {
               // in case not supported by JVM/OS
            }

            // and system property (overrides env. var.)
            hostName = System.getProperty (Constants.CA_HOSTNAME_KEY, hostName);
         }

         if ( System.getProperties ().contains (Constants.CA_STRIP_HOSTNAME) )
         {
            int dotPos = hostName.indexOf ('.');
            if ( dotPos > 0 )
               hostName = hostName.substring (0, dotPos);
         }
      }

      return hostName;
   }

}
