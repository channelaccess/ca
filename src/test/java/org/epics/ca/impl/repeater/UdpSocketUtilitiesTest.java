/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.condition.OS.*;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * The tests here are mainly used to clarify behaviours associated with the
 * sending and receiving of UDP (= User Datagram Protocol) packets.
 *
 * An excellent summary of the Java implementation of this protocol is
 * currently provided at the following URL:
 * https://www.techrepublic.com/article/using-datagrams-in-java/
 */
class UdpSocketUtilitiesTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/
/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeAll
   static void beforeAll()
   {
      // This is a guard condition. There is no point in checking the nehaviour
      // of the SocketUtilities class if the network stack is not appropriately
      // configured for channel access.
      assertThat( NetworkUtilities.verifyTargetPlatformNetworkStackIsChannelAccessCompatible(), is( true ) );
   }

   // -------------------------------------------------------------------------
   // 1.0 Inet Address Tests
   // -------------------------------------------------------------------------

   // The tests in the section below are designed to explicitly explore and
   // capture the definitions and behaviour associated with different types of
   // InetAddress.

   @Test
   void testInetAddress_getAllByName() throws UnknownHostException
   {
      assertThat(Arrays.toString(InetAddress.getAllByName("0.0.0.0")), is("[/0.0.0.0]"));
      assertThat(Arrays.toString(InetAddress.getAllByName(null)), is("[localhost/127.0.0.1]"));
      // On a Mac platform this test returns also the IPV6 addreses which may not be available on other target platforms.
      final InetAddress[] localInetAddresses = InetAddress.getAllByName("localhost");
      Arrays.stream(localInetAddresses).map(InetAddress::toString).forEach(( i ) -> assertThat(i, anyOf(is("localhost/127.0.0.1"), is("localhost/0:0:0:0:0:0:0:1"))));
   }

   @Test
   void testInetAddress_getLoopbackAddress_isNotAnyLocalAddress()
   {
      assertThat(InetAddress.getLoopbackAddress().isAnyLocalAddress(), is(false));
   }

   @Test
   void testInetAddress_getLoopbackAddress_is_as_expected()
   {
      assertThat(InetAddress.getLoopbackAddress().toString(), is("localhost/127.0.0.1"));
   }

   @Test
   void testInetAddress_getLocalHost_isNotAnyLocalAddress() throws UnknownHostException
   {
      assertThat(InetAddress.getLocalHost().isAnyLocalAddress(), is(false));
   }

   @Test
   void
   testInetAddress_getLocalHostAddress_and_getLoopbackAddress() throws UnknownHostException
   {
      System.out.println("The localhost address is: " + InetAddress.getLocalHost());
      System.out.println("The loopback address is: " + InetAddress.getLoopbackAddress());
   }

   @Test
   void testInetAddress_getByName_IP_0_0_0_0_isAnyLocalAddress() throws UnknownHostException
   {
      assertThat(InetAddress.getByName("0.0.0.0").isAnyLocalAddress(), is(true));
   }

   // -------------------------------------------------------------------------
   // 2.0 Inet Socket Address Tests
   // -------------------------------------------------------------------------

   // The tests in the section below are designed to explicitly explore and
   // capture the definitions and behaviour associated with different types of
   // InetSocketAddress.

   @Test
   void testInetSocketAddresss_constructor_withWildcardAddressAndDefinedPort() throws UnknownHostException
   {
      // Note: The wildcard address (selected below) because no address is explicitly
      // defined means "all IpV4 addresses on the local machine". The string
      // representation of this address is "0.0.0.0/0.0.0.0"
      final InetSocketAddress wildcardAddress = new InetSocketAddress(1234);
      assertThat(wildcardAddress.toString(), is("0.0.0.0/0.0.0.0:1234"));
      assertThat(wildcardAddress.isUnresolved(), is(false));
      assertThat(wildcardAddress.getPort(), is(1234));
      assertThat(wildcardAddress.getAddress(), is(InetAddress.getByName("0.0.0.0")));
      assertThat(wildcardAddress.getAddress().toString(), is("0.0.0.0/0.0.0.0"));
      assertThat(wildcardAddress.getHostName(), is("0.0.0.0"));
      assertThat(wildcardAddress.getHostString(), is("0.0.0.0"));
   }

   @Test
   void testInetSocketAddresss_constructor_withWildcardAddressAndEphemeralPort() throws UnknownHostException
   {
      // Note: a port value of zero means the "ephemeral port": some high value port
      // allocated by the operating system.
      final InetSocketAddress ephemeralAddress = new InetSocketAddress(0);
      assertThat(ephemeralAddress.toString(), is("0.0.0.0/0.0.0.0:0"));
      assertThat(ephemeralAddress.isUnresolved(), is(false));
      assertThat(ephemeralAddress.getPort(), is(0));
      assertThat(ephemeralAddress.getAddress(), is(InetAddress.getByName("0.0.0.0")));
      assertThat(ephemeralAddress.getAddress().toString(), is("0.0.0.0/0.0.0.0"));
      assertThat(ephemeralAddress.getHostName(), is("0.0.0.0"));
      assertThat(ephemeralAddress.getHostString(), is("0.0.0.0"));
   }

   @Test
   void testInetSocketAddresss_constructor_withEmptyHostnameString()
   {
      // Note: an empty hostname String gets resolved to the loopback address also known
      // as the 'localhost'.
      final InetSocketAddress emptyHostnameAddress = new InetSocketAddress("", 1234);
      assertThat(emptyHostnameAddress.toString(), is("localhost/127.0.0.1:1234"));
      assertThat(emptyHostnameAddress.isUnresolved(), is(false));
      assertThat(emptyHostnameAddress.getPort(), is(1234));
      assertThat(emptyHostnameAddress.getAddress(), is(InetAddress.getLoopbackAddress()));
      assertThat(emptyHostnameAddress.getAddress().toString(), is("localhost/127.0.0.1"));
      assertThat(emptyHostnameAddress.getHostName(), is("localhost"));
      assertThat(emptyHostnameAddress.getHostString(), is("localhost"));
   }

   @Test
   void testInetSocketAddresss_constructor_withHostnameStringSetToLocalhost()
   {
      // Note: an hostname set to 'localhost' gets resolved to the loopback address.
      final InetSocketAddress loopbackAddress = new InetSocketAddress("localhost", 1234);
      assertThat(loopbackAddress.toString(), is("localhost/127.0.0.1:1234"));
      assertThat(loopbackAddress.isUnresolved(), is(false));
      assertThat(loopbackAddress.getPort(), is(1234));
      assertThat(loopbackAddress.getAddress(), is(InetAddress.getLoopbackAddress()));
      assertThat(loopbackAddress.getAddress().toString(), is("localhost/127.0.0.1"));
      assertThat(loopbackAddress.getHostName(), is("localhost"));
      assertThat(loopbackAddress.getHostString(), is("localhost"));
   }

   @Test
   void testInetSocketAddresss_constructor_withHostnameStringSetToResolvableHost() throws UnknownHostException
   {
      // Note: an hostname set to 'psi.ch' gets resolved to PSI's IP address
      final InetSocketAddress resolvableHostAddress = new InetSocketAddress("psi.ch", 1234);
      assertThat(resolvableHostAddress.isUnresolved(), is(false));
      assertThat(resolvableHostAddress.toString(), is("psi.ch/192.33.120.32:1234"));
      assertThat(resolvableHostAddress.getPort(), is(1234));
      assertThat(resolvableHostAddress.getAddress(), is(InetAddress.getByName("psi.ch")));
      assertThat(resolvableHostAddress.getAddress().toString(), is("psi.ch/192.33.120.32"));
      assertThat(resolvableHostAddress.getHostName(), is("psi.ch"));
      assertThat(resolvableHostAddress.getHostString(), is("psi.ch"));
   }

   @Test
   void testInetSocketAddresss_constructor_withNullHostnameString() throws UnknownHostException
   {
      // Note: If the InetAddress parameter is set to null then this explicitly
      // selects the  wildcard address.
      final InetSocketAddress nullHostAddress = new InetSocketAddress((InetAddress) null, 1234);
      assertThat(nullHostAddress.toString(), is("0.0.0.0/0.0.0.0:1234"));
      assertThat(nullHostAddress.isUnresolved(), is(false));
      assertThat(nullHostAddress.getPort(), is(1234));
      assertThat(nullHostAddress.getAddress(), is(InetAddress.getByName("0.0.0.0")));
      assertThat(nullHostAddress.getAddress().toString(), is("0.0.0.0/0.0.0.0"));
      assertThat(nullHostAddress.getHostName(), is("0.0.0.0"));
      assertThat(nullHostAddress.getHostString(), is("0.0.0.0"));
   }

   @Test
   void testInetSocketAddresss_constructor_withUnresolvedHost()
   {
      // Note: If the InetAddress parameter is set to null then this explicitly
      // selects the  wildcard address.
      final InetSocketAddress unresolvableHostAddress = InetSocketAddress.createUnresolved("somehost", 1234);
      assertThat(unresolvableHostAddress.toString(), is("somehost:1234"));
      assertThat(unresolvableHostAddress.isUnresolved(), is(true));
      assertThat(unresolvableHostAddress.getPort(), is(1234));
      assertThat(unresolvableHostAddress.getAddress(), is(nullValue()));
      assertThat(unresolvableHostAddress.getHostName(), is("somehost"));
      assertThat(unresolvableHostAddress.getHostString(), is("somehost"));
   }

   // -------------------------------------------------------------------------
   // 3.0 Test DatagramPacket
   // -------------------------------------------------------------------------

   // The tests in the section below are designed to explicitly explore and
   // capture the definitions and behaviour associated with the DatagramPacket
   // class.

   @Test
   void testCreateDatagramPacket_withoutDestinationAddress_checkProperties()
   {
      // Verify the properties of a datagram packet which has been created without
      // specifying the destination address.
      final DatagramPacket packet = new DatagramPacket(new byte[] {}, 0);

      // The port and Inet address seem reasonable.
      assertThat(packet.getPort(), is(-1));
      assertThat(packet.getAddress(), is(nullValue()));

      // Verify the somewhat strange behaviour (at least to me) that attempts
      // to read the socket address results in an exception.
      assertThrows(IllegalArgumentException.class, packet::getSocketAddress);

      // Verify that a side effect of setting the port is that attempts to
      // read the socket address no longer results in an exception.
      packet.setPort(33);
      assertThat(packet.getPort(), is(33));
      assertThat(packet.getAddress(), is(nullValue()));
      assertDoesNotThrow(packet::getSocketAddress);
      assertThat(packet.getSocketAddress().toString(), is("0.0.0.0/0.0.0.0:33"));
   }

   @Test
   void testCreateDatagramPacket_withDestinationAddressExplicitlySetToUnconfigured_checkProperties()
   {
      // Create a datagram associated with some socket.
      final DatagramPacket packet = new DatagramPacket(new byte[] {}, 0, InetAddress.getLoopbackAddress(), 33127);

      // The port and Inet address seem reasonable.
      assertThat(packet.getSocketAddress().toString(), is("localhost/127.0.0.1:33127"));

      // Explicitly unset the internet address
      packet.setAddress(null);
      assertThat(packet.getAddress(), is(nullValue()));
      packet.setPort(0);
      assertThat(packet.getPort(), is(0));
   }

   // -------------------------------------------------------------------------
   // 4.0 Test isSocketAvailable
   // -------------------------------------------------------------------------

   @Test
   void testIsSocketAvailable_reportRepeaterPortStatus() throws UnknownHostException
   {
      final InetSocketAddress wildcardAddress = new InetSocketAddress( InetAddress.getLocalHost(), 5065 );
      final boolean portAvailable =  UdpSocketUtilities.isSocketAvailable(wildcardAddress );
      System.out.println( "The repeater port availability is: " + portAvailable );
   }

   @Test
   void testIsSocketAvailable_noSocketsCreated_returnsTrue()
   {
      final InetSocketAddress wildcardAddress = new InetSocketAddress(11111 );
      assertThat(UdpSocketUtilities.isSocketAvailable (wildcardAddress), is(true ) );
   }

   @RepeatedTest( 1000 )
   void testIsSocketAvailable_MultipleOpenAndClose() throws SocketException
   {
      final InetSocketAddress wildcardAddress = new InetSocketAddress(11111);
      assertThat(UdpSocketUtilities.isSocketAvailable(wildcardAddress), is(true));
      try ( DatagramSocket dummy = UdpSocketUtilities.createBroadcastAwareListeningSocket(11111, true) )
      {
         assertThat(UdpSocketUtilities.isSocketAvailable(wildcardAddress), is(false));
      }
   }

   @Test()
   void testIsSocketAvailable_ThreadSafety()
   {
      final int testPort = 11111;
      final int numThreads = 100;
      final ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
      final InetSocketAddress wildcardAddress = new InetSocketAddress(testPort);

      for ( int i = 0; i < numThreads; i++ )
      {
         executorService.submit(() -> {
            assertThat(UdpSocketUtilities.isSocketAvailable(wildcardAddress), is(true));
            try
            {
               try ( DatagramSocket dummy = UdpSocketUtilities.createBroadcastAwareListeningSocket(testPort, true) )
               {
                  assertThat(UdpSocketUtilities.isSocketAvailable(wildcardAddress), is(false));
               }
            }
            catch ( SocketException e )
            {
               e.printStackTrace();
            }
            System.out.println("Thread completed.");
         });
         System.out.println("Thread: " + i + " submitted");
      }
   }

   @Test
   void testIsSocketAvailable_fromSubProcess() throws IOException, InterruptedException
   {
      final int testPort = 2222;

      // Create a broadcast-aware socket in the current JVM and check that it is correctly detected as unavailable.
      final InetSocketAddress wildcardSocketAddress = new InetSocketAddress( testPort );
      try( DatagramSocket socketInUse = UdpSocketUtilities.createBroadcastAwareListeningSocket(testPort, true ) )
      {
         assertThat(UdpSocketUtilities.isSocketAvailable(wildcardSocketAddress ), is(false ) );
      }

      // Check that after the socket has autoclosed the socket is once again available
      assertThat(UdpSocketUtilities.isSocketAvailable( wildcardSocketAddress ), is(true ) );

      // Spawn a test that will occupy the test socket for 5 seconds.
      final String classPath =  System.getProperty( "java.class.path", "<java.class.path not found>" );
      final String classWithMainMethod =  UdpSocketReserver.class.getName();
      final String wildcarAddress ="0.0.0.0";
      final int testTimeInMilliseconds = 2000;
      final List<String> commandLine = Arrays.asList( "java", "-cp", classPath,
                                                      "-Djava.net.preferIPv4Stack=true",
                                                      "-Djava.net.preferIPv6Stack=false",
                                                      classWithMainMethod,
                                                      wildcarAddress,
                                                      String.valueOf( testPort ),
                                                      String.valueOf( testTimeInMilliseconds ) );
      final Process process = new ProcessBuilder().command( commandLine ).inheritIO().start();

      // After only one second check that the socket is no longer available.
      ProcessStreamConsumer.consumeFrom( process );
      assertThat( process.isAlive(), is( true ) );
      process.waitFor( 1, TimeUnit.SECONDS );
      assertThat( process.isAlive(), is( true ) );
      assertThat( UdpSocketUtilities.isSocketAvailable( wildcardSocketAddress ), is(false ) );

      // Wait for the process to die and check that the socket has become available again
      process.waitFor();
      assertThat( process.exitValue(), is( 0 ) );
      assertThat(UdpSocketUtilities.isSocketAvailable(wildcardSocketAddress ), is(true ) );
   }

   @ValueSource( booleans = { false, true } )
   @ParameterizedTest
   void testIsSocketAvailable_wildcardAddressShareability( boolean shareable) throws SocketException
   {
      final InetSocketAddress wildcardSocketAddress = new InetSocketAddress(22222 );

      // Verify the precondition: there should be nothing running on this socket at the start of
      // the test so the socket should be available !
      assertThat(UdpSocketUtilities.isSocketAvailable(wildcardSocketAddress ), is(true ) );

      // Note: socket is opened using try-with-resources to ensure that the socket gets autoclosed regardless of outcome.
      // Note: the socket-in-use is also bound to the wildcard socket address.
      try ( final DatagramSocket socketInUse = UdpSocketUtilities.createUnboundSendSocket() )
      {
         socketInUse.setReuseAddress( shareable );
         socketInUse.bind( wildcardSocketAddress );

         // Verify that irrespective of the reusability mode of the new socket the
         // observer method always reports that the socket is no longer available.
         assertThat(UdpSocketUtilities.isSocketAvailable(wildcardSocketAddress ), is(false ) );
      }
   }

   @ValueSource( booleans = { false, true } )
   @ParameterizedTest
   void testIsSocketAvailable_localHostAddressShareability( boolean shareable) throws SocketException, UnknownHostException
   {
      final InetSocketAddress localHostSocketAddress = new InetSocketAddress( InetAddress.getLocalHost(), 33333 );

      // Verify the precondition: there should be nothing running on this socket at the start of
      // the test so the socket should be available !
      assertThat(UdpSocketUtilities.isSocketAvailable(localHostSocketAddress ), is(true ) );

      // Note: socket is opened using try-with-resources to ensure that the socket gets autoclosed regardless of outcome
      // Note: the socket-in-use is also bound to the localhost socket address.
      try ( final DatagramSocket socketInUse = UdpSocketUtilities.createUnboundSendSocket() )
      {
         socketInUse.setReuseAddress( shareable );
         socketInUse.bind( localHostSocketAddress );

         // Verify that irrespective of the reusability mode of the new socket the
         // observer method always reports that the socket is no longer available.
         assertThat(UdpSocketUtilities.isSocketAvailable(localHostSocketAddress ), is(false ) );
      }
   }

   @ValueSource( booleans = { false, true } )
   @ParameterizedTest
   void testIsSocketAvailable_loopbackAddressShareability( boolean shareable) throws SocketException
   {
      final InetSocketAddress loopbackSocketAddress = new InetSocketAddress( InetAddress.getLoopbackAddress(), 44444 );

      // Verify the precondition: there should be nothing running on this socket at the start of
      // the test so the socket should be available !
      assertThat(UdpSocketUtilities.isSocketAvailable(loopbackSocketAddress ), is(true ) );

      // Note: socket is opened using try-with-resources to ensure that the socket gets autoclosed regardless of outcome.
      // Note: the socket-in-use is also bound to the loopback socket address.
      try ( final DatagramSocket socketInUse = UdpSocketUtilities.createUnboundSendSocket() )
      {
         socketInUse.setReuseAddress( shareable );
         socketInUse.bind( loopbackSocketAddress );

         // Verify that irrespective of the reusability mode of the new socket the
         // observer method always reports that the socket is no longer available.
         assertThat(UdpSocketUtilities.isSocketAvailable(loopbackSocketAddress ), is(false ) );
      }
   }

   // -------------------------------------------------------------------------
   // 5.0 Test createEphemeralSendSocket and createUnboundSendSocket
   // -------------------------------------------------------------------------

   @ValueSource( booleans = {false, true } )
   @ParameterizedTest
   void testCreateEphemeralSendSocketProperties( boolean broadcastEnable) throws SocketException
   {
      final DatagramSocket socketReferenceCopy;
      try ( DatagramSocket sendSocket = UdpSocketUtilities.createEphemeralSendSocket(broadcastEnable ) )
      {
         socketReferenceCopy = sendSocket;

         // Verify that the socket has been bound to an ephemeral port
         assertThat( sendSocket.isBound(), is(true));

         // Verify that the local address does not reveal the address to which it has become bound
         assertThat( sendSocket.getLocalAddress().toString(), is( "0.0.0.0/0.0.0.0" ) );

         // Verify that the port that has been allocated is consistent with
         // this being an ephemeral port. Unfortunately due to OS implementation
         // variations this is almost impossible to test. About the only thing
         // we can say about an ephemeral port is that the returned value will
         // be non-zero !
         assertThat( sendSocket.getLocalPort(), not( is( 0 ) ) );

         // Verify the states of the socket when it is not yet connected.
         assertThat( sendSocket.isConnected(), is(false));
         assertThat( sendSocket.getPort(), is(-1 )) ;
         assertThat( sendSocket.getInetAddress(), is (nullValue() ) );
         assertThat( sendSocket.getRemoteSocketAddress(), is( nullValue()) );

         // Verify that the socket isn't closed.
         assertThat( sendSocket.isClosed(), is(false));

         // Verify that the socket timeout is set to infinity (=0).
         assertThat( sendSocket.getSoTimeout(), is(0));

         // Verify that the broadcast flag is set as specified.
         assertThat( sendSocket.getBroadcast(), is( broadcastEnable ) );

         // Verify that the socket has not been created in a state where other
         // OS clients can use it.
         assertThat( sendSocket.getReuseAddress(), is(false) );

         // Verify the states of the socket following connection to the loopback adapter.
         sendSocket.connect( new InetSocketAddress( InetAddress.getLoopbackAddress(), 1234 ) );
         assertThat( sendSocket.isConnected(), is(true ) );
         assertThat( sendSocket.isClosed(), is(false) );
         assertThat( sendSocket.getRemoteSocketAddress().toString(), is( "localhost/127.0.0.1:1234" ) );
      }

      // Verify that the socket gets closed following the termination of the
      // try-with-resources block.
      assertThat( socketReferenceCopy, notNullValue() );
      assertThat( socketReferenceCopy.isConnected(), is(true ) );
      assertThat( socketReferenceCopy.isClosed(), is(true ));

      // Verify that socket remain bound even after they have been closed.
      assertThat( socketReferenceCopy.isBound(), is( true ) );
   }

   @Test
   void testCreateUnboundSendSocketProperties() throws  IOException
   {
      final DatagramSocket socketReferenceCopy;
      try ( DatagramSocket sendSocket = UdpSocketUtilities.createUnboundSendSocket() )
      {
         socketReferenceCopy = sendSocket;

         // Verify that the socket has NOT been bound to an ephemeral port and wildcard address
         assertThat( sendSocket.isBound(), is(false ) );
         assertThat( sendSocket.getLocalAddress().toString(), is( "0.0.0.0/0.0.0.0" ) );
         assertThat( sendSocket.getLocalPort(), is( 0 ) );

         // Verify the states of the socket when it is not yet connected.
         assertThat( sendSocket.isConnected(), is(false));
         assertThat( sendSocket.getPort(), is(-1 )) ;
         assertThat( sendSocket.getInetAddress(), is (nullValue() ) );
         assertThat( sendSocket.getRemoteSocketAddress(), is( nullValue()) );

         // Verify that the socket isn't closed.
         assertThat( sendSocket.isClosed(), is(false));

         // Verify that the socket timeout is set to infinity (=0).
         assertThat( sendSocket.getSoTimeout(), is(0));

         // Verify that the broadcast flag is set to true by default.
         // Note: although this is not necessary for the CA repeater.
         assertThat( sendSocket.getBroadcast(), is(true ));

         // Verify that the socket has not been created in a state where other
         // OS clients can use it.
         assertThat( sendSocket.getReuseAddress(), is(false) );

         // Now perform a send operation
         final DatagramPacket datagramPacket = new DatagramPacket( new byte[] { (byte) 0xAA, (byte) 0xBB }, 2, new InetSocketAddress( InetAddress.getLoopbackAddress(), 9998 ) );
         sendSocket.send( datagramPacket );

         // Verify that the socket has now been bound to an ephemeral port.
         assertThat( sendSocket.isBound(), is(true));

         // Verify that the local address does not reveal the address to which it has become bound
         assertThat( sendSocket.getLocalAddress().toString(), is( "0.0.0.0/0.0.0.0" ) );

         // Verify that the port that has been allocated is consistent with
         // this being an ephemeral port. Unfortunately due to OS implementation
         // variations this is almost impossible to test. About the only thing
         // we can say about an ephemeral port is that the returned value will
         // be non-zero !
         assertThat( sendSocket.getLocalPort(), not( is( 0 ) ) );

         // Verify that attempts to bind it to a different address result in a SocketException
         // with a message saying that the socket is already bound.
         final Throwable throwable = assertThrows( SocketException.class, () -> sendSocket.bind(  new InetSocketAddress( InetAddress.getLocalHost(), 9999 ) ) );
         assertThat( throwable.getMessage(), is( "already bound" ) );

         // Verify the states of the socket following connection to the loopback adapter.
         sendSocket.connect( new InetSocketAddress( InetAddress.getLoopbackAddress(), 1234 ) );
         assertThat( sendSocket.isConnected(), is(true ) );
         assertThat( sendSocket.isClosed(), is(false) );
         assertThat( sendSocket.getRemoteSocketAddress().toString(), is( "localhost/127.0.0.1:1234" ) );
      }

      // Verify that the socket gets closed following the termination of the
      // try-with-resources block.
      assertThat( socketReferenceCopy, notNullValue() );
      assertThat( socketReferenceCopy.isConnected(), is(true ) );
      assertThat( socketReferenceCopy.isClosed(), is(true ));

      // Verify that socket remain bound even after they have been closed.
      assertThat( socketReferenceCopy.isBound(), is( true ) );

      // Verify that socket remain bound even after they have been closed.
      assertThat( socketReferenceCopy.isBound(), is( true ) );
   }

   @Test
   void testCreateUnboundSendSocket_BindBehaviourFollowingConnect() throws  IOException
   {
      try ( DatagramSocket sendSocket = UdpSocketUtilities.createUnboundSendSocket() )
      {
         // Verify that the socket has NOT been bound to an ephemeral port and wildcard address
         assertThat( sendSocket.isBound(), is(false ) );

         // Verify that specifying the remote address also brings bout a bind operation
         sendSocket.connect( new InetSocketAddress( InetAddress.getLoopbackAddress(), 1234 ) );
         assertThat( sendSocket.isBound(), is(true ) );
      }
   }

   // Note: For IPV4 240.0.0.0 is "reserved for future use" and should never get routed.
   @ValueSource( strings = { "127.0.0.1", "240.0.0.0", "google.com" } )
   @ParameterizedTest
   void testSocketSend_investigateMaximumLengthOfDatagram( String inetAddress ) throws UnknownHostException
   {
      // Create a datagram with a buffer likely big enough to exceed the maximum send length constraint
      final int bufferSize = 100_000;
      final DatagramPacket packet = new DatagramPacket( new byte[ bufferSize ] , bufferSize, InetAddress.getByName( inetAddress ), 7712 );

      Exception exception = null;
      int maxDatagramLength = bufferSize;
      try ( DatagramSocket sendSocket = UdpSocketUtilities.createEphemeralSendSocket(true ) )
      {
         for ( int i = 0; i < bufferSize; i = i + 100 )
         {
            packet.setLength( i );
            sendSocket.send( packet );
            maxDatagramLength = i;
         }
      }
      catch ( Exception ex )
      {
         exception = ex;
      }
      assertNotNull( exception );
      assertThat( exception.getClass(), is( IOException.class ) );
      assertThat( exception.getMessage(), anyOf( containsString( "Message too long" ),
                                                 containsString( "sendto failed" ) ) );
      System.out.println( "The exception message details were: '" +  exception.getMessage() + "'." );
      System.out.println( "The maximum datagram length for InetAddress: '" + inetAddress + "' is " + maxDatagramLength + " bytes." );
   }

   @Test
   void testSocketSend_investigateAttemptToBroadcastWhenSocketNotBroadcastEnabled() throws UnknownHostException, SocketException
   {
      // Create a datagram whose detsination is the universal broadcast address.
      final DatagramPacket packet = new DatagramPacket( new byte[] { (byte) 0xAA } , 1, InetAddress.getByName( "255.255.255.255" ), 6904 );

      // Create a socket which is not broadcast enabled.
      final Exception exception;
      try ( DatagramSocket sendSocket = UdpSocketUtilities.createEphemeralSendSocket(false ) )
      {
         exception = assertThrows( IOException.class, () -> sendSocket.send( packet ) );
      }
      assertThat( exception.getClass(), is( IOException.class ) );
      assertThat( exception.getMessage(), anyOf( containsString( "Permission denied" ),
                                                 containsString( "Can't assign requested address (sendto failed)" ),
                                                 containsString( "No buffer space available (sendto failed)" ) ) );
      System.out.println( "The exception message details were: '" +  exception.getMessage() + "'." );
   }

   @Test
   void testSocketSend_investigateNobodyListening() throws UnknownHostException, SocketException
   {
      // Create a datagram destined for the IPV6 blackhole internet address
      final DatagramPacket packet = new DatagramPacket( new byte[] { (byte) 0xAA } , 1, InetAddress.getByName( "100::" ), 44231 );

      // Create a socket which is not broadcast enabled.
      final Exception exception;
      try ( DatagramSocket sendSocket = UdpSocketUtilities.createEphemeralSendSocket(false ) )
      {
         exception = assertThrows( IOException.class, () -> sendSocket.send( packet ) );
      }
      assertThat( exception.getMessage(), anyOf( containsString( "Network is unreachable"),
                                                 containsString( "No route to host (sendto failed)" ),
                                                 containsString( "No buffer space available (sendto failed)" ),
                                                 containsString( "Protocol family unavailable" ) ) );
      System.out.println( "The exception message details were: '" +  exception.getMessage() + "'." );
   }


   // -------------------------------------------------------------------------
   // 6.0 Test createBroadcastAwareListeningSocket
   // -------------------------------------------------------------------------

   @ParameterizedTest
   @ValueSource( booleans = { true, false } )
   void testCreateBroadcastAwareListeningSocket_DefaultProperties( boolean shareable ) throws SocketException
   {
      try( DatagramSocket socket = UdpSocketUtilities.createBroadcastAwareListeningSocket(1234, shareable ) )
      {
         // Verify that the socket is not closed.
         assertThat( socket.isClosed(), is(false ) );

         // Verify that the socket is configured for compatibility with broadcasts.
         assertThat( socket.getBroadcast(), is(true ) );

         // Verify that the socket is not connected. That means that it will not perform
         // any filtering on the packets it received, nor add any destination information
         // to datagram packets that it is asked to send.
         assertThat( socket.isConnected(), is(false ) );

         // Verify that the socket's reuseability mode is as requested.
         // Note: socket reuse may not be enabled on some platforms.
         // See the excellent article on Stack Exchange:
         // https://stackoverflow.com/questions/14388706/how-do-so-reuseaddr-and-so-reuseport-differ
         final String reason = "The socket reuse mode was not as configured. Perhaps it is not available on the current runtime platform.";
         assertThat( reason, socket.getReuseAddress(), is( shareable) );

         // Verify that the local state of socket shows that it is bound to Port 1234 on the wildcard address.
         assertThat( socket.isBound(), is(true ) );
         assertThat( socket.getLocalSocketAddress().toString(), is("0.0.0.0/0.0.0.0:1234"));
         assertThat( socket.getLocalAddress().toString(), is("0.0.0.0/0.0.0.0"));
         assertThat( socket.getLocalPort(), is(1234 ) );

         // Verify the size of the receive buffer on this platform.
         // On a MacBook Pro vintage 2017 this is 65507.
         assertThat( socket.getReceiveBufferSize(), is(greaterThan(16384)));

         // Verify the remote state of the socket shows that it is not configured to send
         // its packets to anywhere in particular. This means the information must be
         // provided in each Datagram packet that the user wishes to send.
         assertThat( socket.getRemoteSocketAddress(), is(nullValue()));
         assertThat( socket.getPort(), is(-1));
         assertThat( socket.getInetAddress(), is(nullValue()));

         // Close socket and verify that it is now reported as closed
         socket.close();
         assertThat( socket.isClosed(), is(true));

         // Verify that sockets remain bound even after they have been closed.
         assertThat(socket.isBound(), is(true));
      }
   }

   @Test
   void testCreateBroadcastAwareListeningSocket_verifyShareablePortsAreShareable() throws SocketException
   {
      try( DatagramSocket socket = UdpSocketUtilities.createBroadcastAwareListeningSocket(1234, true ) )
      {
         assertDoesNotThrow(() -> UdpSocketUtilities.createBroadcastAwareListeningSocket(1234, true));
      }
   }

   @EnabledOnOs( {LINUX, MAC} )
   @Test
   void testCreateBroadcastAwareListeningSocket_verifyUnshareablePortsAreUnshareableOnLinuxOrMac() throws SocketException
   {
      // Note:
      // This test and the one below it are identical except the different OS throw different
      // exceptions, albeit both of the same subclass of SocketException.
      try( DatagramSocket socket = UdpSocketUtilities.createBroadcastAwareListeningSocket(1234, false ) )
      {
         assertThrows( BindException.class, () -> UdpSocketUtilities.createBroadcastAwareListeningSocket(1234, false));
      }
   }

   @EnabledOnOs( WINDOWS )
   @Test
   void testCreateBroadcastAwareListeningSocket_verifyUnshareablePortsAreUnshareableOnWindows() throws SocketException
   {
      // Note:
      // This test and the one below it are identical except the different OS throw different
      // exceptions, albeit both of the same subclass of SocketException.
      try( DatagramSocket socket = UdpSocketUtilities.createBroadcastAwareListeningSocket(1234, false ) )
      {
         assertThrows(ConnectException.class, () -> UdpSocketUtilities.createBroadcastAwareListeningSocket(1234, false));
      }
   }

   @Test
   void testCreateBroadcastAwareListeningSocket_autoclose() throws SocketException
   {
      final DatagramSocket socketReferenceCopy;
      try ( DatagramSocket listeningSocket = UdpSocketUtilities.createBroadcastAwareListeningSocket(1234, false ) )
      {
         socketReferenceCopy = listeningSocket;
         assertThat( listeningSocket.isClosed(), is( false ) );
      }
      assertThat( socketReferenceCopy.isClosed(), is( true ) );
   }

   // -------------------------------------------------------------------------
   // 7.0 Test socket broadcast capabilities on current platform
   // -------------------------------------------------------------------------

   @Test
   void integrationTestBroadcastCapability_matchesCurrentPlatformExpectation() throws IOException, ExecutionException, InterruptedException
   {
      // Currently (2020-04-29) on LINUX and MAC platforms broadcast capability is often supported.
      // But note when running on a MAC platform using VPN this test fails !
      boolean broadcastsSupportedOnCurrentPlatform = LINUX.isCurrentOs() || MAC.isCurrentOs();

      // Change this address to the network UDP broadcast address. The following
      // address (255.255.255.255) should be applicable on all network interfaces
      // where broadcast capability is available.
      final InetAddress broadcastAddress = InetAddress.getByName( "255.255.255.255" );
      final int testPort = 8888;

      final DatagramPacket receivePacket = new DatagramPacket (new byte[ 10 ], 10 );

      try ( final DatagramSocket listenSocket = UdpSocketUtilities.createBroadcastAwareListeningSocket(testPort, false );
            final DatagramSocket sendSocket = UdpSocketUtilities.createUnboundSendSocket() )
      {
         final ExecutorService executor = Executors.newSingleThreadExecutor();
         final Future<?> f = executor.submit(() -> {
            System.out.println( "Receive thread starting to listen on address: '" + listenSocket.getLocalSocketAddress() + "'." );
            try
            {
               listenSocket.receive( receivePacket );
               System.out.println( "Received new packet from: '" + receivePacket.getSocketAddress() + "'.");
            }
            catch ( Exception ex )
            {
               System.out.println( "Received thread interrupted by exception: " + ex.getMessage() + "." );
            }
            System.out.println( "Receiver task completed.");
         });

         System.out.println( "Sending packet..." );
         final DatagramPacket sendPacket = new DatagramPacket( new byte[] { (byte) 0xAA, (byte) 0xBB }, 2 );
         sendPacket.setSocketAddress( new InetSocketAddress( broadcastAddress, testPort ) );
         assertDoesNotThrow( () -> sendSocket.send( sendPacket ), "The send operation generated an exception. Is this test being run behind a VPN where broadcasts are not supported ?" );
         System.out.println("Send completed.");

         try
         {
            System.out.println( "Waiting for data...");
            f.get(500, TimeUnit.MILLISECONDS );
            System.out.println( "Wait terminated." );
            assertThat( receivePacket.getLength(), is( sendPacket.getLength() ) );
            assertThat( receivePacket.getData()[0], is( sendPacket.getData()[0] ) );
            assertThat( receivePacket.getData()[1], is( sendPacket.getData()[1] ) );
            assertThat( broadcastsSupportedOnCurrentPlatform, is( true ) );
         }
         catch ( TimeoutException ex )
         {
            System.out.println( "Timeout - data not received." );
            f.cancel( true );
            assertThat( broadcastsSupportedOnCurrentPlatform, is( false ) );
         }
         executor.shutdown();
      }
   }

   // -------------------------------------------------------------------------
   // 8.0 Test data transfer capabilities on current platform
   // -------------------------------------------------------------------------

   @ValueSource( booleans = { true, false } )
   @ParameterizedTest
   void integrationTestDataTransfer( boolean bindSendSocket ) throws Exception
   {
      final DatagramPacket receivePacket = new DatagramPacket (new byte[ 10 ], 10);
      final DatagramPacket sendPacket = new DatagramPacket( new byte[] { (byte) 0xAA, (byte) 0xBB }, 2 );

      try ( final DatagramSocket listenSocket = UdpSocketUtilities.createBroadcastAwareListeningSocket(12345, false );
            final DatagramSocket sendSocket = bindSendSocket ? UdpSocketUtilities.createEphemeralSendSocket(false ) : UdpSocketUtilities.createUnboundSendSocket() )
      {
         // Verify that the send socket is bound or unbound as requested.
         assertThat( sendSocket.isBound(), is( bindSendSocket ) );

         // Configure the destination address to send to
         sendSocket.connect( InetAddress.getLoopbackAddress(), 12345 );
         assertThat( sendSocket.isConnected(), is(true));

         final ExecutorService executor = Executors.newSingleThreadExecutor();
         final Future<?> f = executor.submit(() -> {
            System.out.println( "Receive thread started");
            try
            {
               listenSocket.receive( receivePacket );
               System.out.println( "Received new packet from: " + receivePacket.getSocketAddress() );
            }
            catch ( Exception ex )
            {
               System.out.println( "Exception thrown !" + ex.getMessage() );
            }
            System.out.println( "Receive thread completed");
         });

         System.out.println( "About to send...");
         sendSocket.send( sendPacket );
         System.out.println( "Waiting for receive to complete");
         f.get();
         System.out.println( "Receive completed");
         executor.shutdown();
      }
      assertThat ( receivePacket.getLength(), is( 2 ) );
      assertThat ( receivePacket.getSocketAddress() instanceof InetSocketAddress , is( true  ) );
      assertThat ( ((InetSocketAddress) receivePacket.getSocketAddress()).getAddress(), not( is( InetAddress.getByName( "0.0.0.0" ) ) ) );
      assertThat ( ((InetSocketAddress) receivePacket.getSocketAddress()).getPort(), not( is( 0 ) ) );
      assertThat ( receivePacket.getData()[0], is( sendPacket.getData()[0] ) );
      assertThat ( receivePacket.getData()[1], is( sendPacket.getData()[1] ) );
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
