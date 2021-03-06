/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.time.StopWatch;
import org.epics.ca.impl.reactor.Reactor;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

@SuppressWarnings( "SpellCheckingInspection" )
class TcpTransportTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( TcpTransportTest.class );

   private ContextImpl context;
   private SocketChannel channel;
   private TcpTransport transport;
   private ResponseHandlers.ResponseHandler handler;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   @BeforeEach
   @SuppressWarnings( "unchecked" )
   void setupTcpTransport()
   {
      // Create a mock for the first argument of the TCPTransport constructor
      context = Mockito.mock( ContextImpl.class );

      // Create a mock for the second argument
      final TransportClient client = Mockito.mock( TransportClient.class);

      // Create a mock for the third argument
      handler = Mockito.mock( ResponseHandlers.ResponseHandler.class );

      // Create a mock for the fourth argument
      channel = Mockito.mock( SocketChannel.class );

      // Create some values for the other arguments passed to the constructor
      final short minorRevision = 2;
      final int priority = 1;

      // Create some intermediate objects which are needed to support the constructor.
      // mocking behaviour...
      // Note: INetSocketAddress cant be mocked (because it declares hashcode and equals as final).
      // So we create a real one here. For the purpose of the test the difference is irrelevant.
      final InetSocketAddress socketAddress = new InetSocketAddress(1234 );

      @SuppressWarnings( "rawtypes" )
      final ScheduledFuture scheduledFuture = Mockito.mock( ScheduledFuture.class );

      final ScheduledExecutorService scheduledExecutorService = Mockito.mock ( ScheduledExecutorService.class );
      final TransportRegistry transportRegistry = Mockito.mock( TransportRegistry.class );
      final Socket socket = Mockito.mock( Socket.class );

      // Now provide the mocking behavioural support to allow the TCPTransport constructor to get invoked.
      Mockito.when( channel.socket() ).thenReturn( socket );
      Mockito.when( socket.getRemoteSocketAddress() ).thenReturn( socketAddress );
      Mockito.when( context.getScheduledExecutor() ).thenReturn( scheduledExecutorService );

      //noinspection unchecked
      Mockito.when( scheduledExecutorService.scheduleWithFixedDelay( any(), ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(), any() ) )
            .thenReturn( scheduledFuture );
      Mockito.when( context.getTransportRegistry() ).thenReturn( transportRegistry );

      // Ok, we now have everything in place to construct our TCPTransport that we will test/
      // Go and build the test object.
      transport = new TcpTransport(context, client, handler, channel, minorRevision, priority );
   }

   @AfterEach
   void afterEach()
   {
      final Reactor reactor = Mockito.mock( Reactor.class );
      final ArgumentCaptor<SelectableChannel> captor = ArgumentCaptor.forClass( SelectableChannel.class );
      Mockito.when( context.getReactor() ).thenReturn( reactor );
      transport.close( false );
      verify( reactor ).unregisterAndClose( captor.capture() );
   }

   @Test
   void testCaCommandWithNoPayload_HeaderSuppliedInOneChunk() throws IOException
   {
      // We will use CA_PROTO_EVENT_CANCEL as an example as
      // (a) it has no payload
      // (b) it allows interesting values for some of the header fields
      final short cmdVersion = 0x0017;
      final short payloadSize = 0x0000; // must be zero.
      final short dataType = 0x0001;    // must correspond to some DBR type (same as request)
      final short dataCount = 0x0000;   // must be zero
      final int param1 = 0xDEADBEEF;     // SID
      final int param2 = 0xDABBAD00;     // SubscriptionID

      final ByteBuffer bufSocketRead1 = (ByteBuffer) ByteBuffer.allocate(16)
            .putShort( cmdVersion )
            .putShort( payloadSize )
            .putShort( dataType )
            .putShort( dataCount )
            .putInt( param1 )
            .putInt( param2 )
            .flip ();

      // Create the mocking behaviour which passes our prepared buffer to the
      // object under test when it reads the channel
      Mockito.when (channel.read (ArgumentMatchers.<ByteBuffer> any ())).thenAnswer (i -> {
         // The first time the channel is read return our command.
         final ByteBuffer suppliedBuf = i.getArgument (0);
         suppliedBuf.put (bufSocketRead1);
         return 16;
      }).thenAnswer (i -> {
         // The second time our channel is read return an indicator showing
         // that there are no further bytes to be read.
         return 0;
      });

      // Create the mocking behaviour which ensures that a selection event
      // triggers the socket read handling.
      final SelectionKey selectionKey = Mockito.mock (SelectionKey.class);
      Mockito.when (selectionKey.isValid ()).thenReturn (true);
      Mockito.when (selectionKey.readyOps ()).thenReturn (1);

      // Go ahead and trigger the processing which reads data from the socket.
      transport.handleEvent( selectionKey );

      // Now verify our expectation that the handler class gets called with the expected arguments
      ArgumentCaptor<InetSocketAddress> captor1 = ArgumentCaptor.forClass (InetSocketAddress.class);
      ArgumentCaptor<Transport> captor2 = ArgumentCaptor.forClass (Transport.class);
      ArgumentCaptor<Header> captor3 = ArgumentCaptor.forClass (Header.class);
      ArgumentCaptor<ByteBuffer> captor4 = ArgumentCaptor.forClass (ByteBuffer.class);
      verify (handler).handleResponse (captor1.capture (), captor2.capture (), captor3.capture (), captor4.capture ());

      // Verify that the passed InetSocketAddress had the expected port
      assertEquals (1234, captor1.getValue ().getPort ());

      // Verify that the transport reference was passed as expected
      assertEquals (transport, captor2.getValue ());

      // Verify all the values in the supplied header
      assertEquals (0x0017, captor3.getValue ().command);
      assertEquals (0x0000, captor3.getValue ().payloadSize);
      assertEquals (0x0001, captor3.getValue ().dataType);
      assertEquals (0x0000, captor3.getValue ().dataCount);
      assertEquals (0xDEADBEEF, captor3.getValue ().parameter1);
      assertEquals (0xDABBAD00, captor3.getValue ().parameter2);

      // Verify that the first byte in the supplied Bytebuffer is the first
      // byte in the header (which is our command).
      assertEquals (0x0017, captor4.getValue ().getShort (0));

      // Verify that no further interactions take place
      verifyNoMoreInteractions (handler);
   }

   @Test
   void testCaCommandWithNoPayload_HeaderSuppliedInTwoChunks() throws IOException
   {
      // We will use CA_PROTO_EVENT_CANCEL as an example as
      // (a) it has no payload
      // (b) it allows interesting values for some of the header fields
      final short cmdVersion = 0x0017;
      final short payloadSize = 0x0000; // must be zero.
      final short dataType = 0x0001;    // must correspond to some DBR type (same as request)
      final short dataCount = 0x0000;   // must be zero
      final int param1 = 0xDEADBEEF;     // SID
      final int param2 = 0xDABBAD00;     // SubscriptionID

      final ByteBuffer bufSocketRead1 = (ByteBuffer) ByteBuffer.allocate (8)
            .putShort (cmdVersion)
            .putShort (payloadSize)
            .putShort (dataType)
            .putShort (dataCount)
            .flip ();

      final ByteBuffer bufSocketRead2 = (ByteBuffer) ByteBuffer.allocate (8)
            .putInt (param1)
            .putInt (param2)
            .flip ();

      // Create the mocking behaviour which passes our prepared buffers to the
      // object under test when it reads the channel
      Mockito.when (channel.read (ArgumentMatchers.<ByteBuffer> any ())).thenAnswer (i -> {
         // The first time the channel is read return our command.
         final ByteBuffer suppliedBuf = i.getArgument (0);
         suppliedBuf.put (bufSocketRead1);
         return 8;
      }).thenAnswer (i -> {
         // The first time the channel is read return our command.
         final ByteBuffer suppliedBuf = i.getArgument (0);
         suppliedBuf.put (bufSocketRead2);
         return 8;
      }).thenAnswer (i -> {
         // The third time our channel is read return an indicator showing
         // that there are no further bytes to be read.
         return 0;
      });

      // Create the mocking behaviour which ensures that a selection event
      // triggers the socket read handling
      final SelectionKey selectionKey = Mockito.mock (SelectionKey.class);
      Mockito.when (selectionKey.isValid ()).thenReturn (true);
      Mockito.when (selectionKey.readyOps ()).thenReturn (1);

      // Go ahead and trigger the processing which reads data from the socket.
      transport.handleEvent (selectionKey);

      // Now verify our expectation that the handler class gets called with the expected arguments
      ArgumentCaptor<InetSocketAddress> captor1 = ArgumentCaptor.forClass (InetSocketAddress.class);
      ArgumentCaptor<Transport> captor2 = ArgumentCaptor.forClass (Transport.class);
      ArgumentCaptor<Header> captor3 = ArgumentCaptor.forClass (Header.class);
      ArgumentCaptor<ByteBuffer> captor4 = ArgumentCaptor.forClass (ByteBuffer.class);
      verify (handler).handleResponse (captor1.capture (), captor2.capture (), captor3.capture (), captor4.capture ());

      // Verify that the passed InetSocketAddress had the expected port
      assertEquals (1234, captor1.getValue ().getPort ());

      // Verify that the transport reference was passed as expected
      assertEquals (transport, captor2.getValue ());

      // Verify all the values in the supplied header
      assertEquals (0x0017, captor3.getValue ().command);
      assertEquals (0x0000, captor3.getValue ().payloadSize);
      assertEquals (0x0001, captor3.getValue ().dataType);
      assertEquals (0x0000, captor3.getValue ().dataCount);
      assertEquals (0xDEADBEEF, captor3.getValue ().parameter1);
      assertEquals (0xDABBAD00, captor3.getValue ().parameter2);

      // Verify that the first byte in the supplied Bytebuffer is the first
      // byte in the header (which is our command).
      assertEquals (0x0017, captor4.getValue ().getShort (0));

      // Verify no further interactions take place
      verifyNoMoreInteractions (handler);
   }

   @Test
   void testCaCommandWithPayload_HeaderSuppliedInOneChunk() throws IOException
   {
      // We will use CA_PROTO_READ_NOTIFY with an element count of 1 and a long payload as an example
      final short cmdVersion = 0x000F;
      final short payloadSize = 0x0004; // DBR_LONG has a payload size of 4 bytes
      final short dataType = 0x0005;    // DBR_LONG ID
      final short dataCount = 0x0001;   // must be 1
      final int param1 = 0xDEADBEEF;     // SID
      final int param2 = 0xDABBAD00;     // IOID

      final ByteBuffer bufSocketRead1 = (ByteBuffer) ByteBuffer.allocate (16)
            .putShort (cmdVersion)
            .putShort (payloadSize)
            .putShort (dataType)
            .putShort (dataCount)
            .putInt (param1)
            .putInt (param2)
            .flip ();

      final ByteBuffer bufSocketRead2 = (ByteBuffer) ByteBuffer.allocate (4)
            .putInt (0xCAFEBABE)
            .flip ();

      // Create the mocking behaviour which passes our prepared buffers to the
      // object under test when it reads the channel
      Mockito.when (channel.read (ArgumentMatchers.<ByteBuffer> any ())).thenAnswer (i -> {
         // The first time the channel is read return the buffer containing the header
         final ByteBuffer suppliedBuf = i.getArgument (0);
         suppliedBuf.put (bufSocketRead1);
         return 16;
      }).thenAnswer (i -> {
         // The second time our channel is read return the buffer containing the payload
         final ByteBuffer suppliedBuf = i.getArgument (0);
         suppliedBuf.put (bufSocketRead2);
         return 4;
      }).thenAnswer (i -> {
         // The third time our channel is read return an indicator showing
         // that there are no further bytes to be read.
         return 0;
      });

      // Create the mocking behaviour which ensures that a selection event
      // triggers the socket read handling
      final SelectionKey selectionKey = Mockito.mock (SelectionKey.class);
      Mockito.when (selectionKey.isValid ()).thenReturn (true);
      Mockito.when (selectionKey.readyOps ()).thenReturn (1);

      // Go ahead and trigger the processing which reads data from the socket.
      transport.handleEvent (selectionKey);

      // Now verify our expectation that the handler class gets called with the expected arguments
      ArgumentCaptor<InetSocketAddress> captor1 = ArgumentCaptor.forClass (InetSocketAddress.class);
      ArgumentCaptor<Transport> captor2 = ArgumentCaptor.forClass (Transport.class);
      ArgumentCaptor<Header> captor3 = ArgumentCaptor.forClass (Header.class);
      ArgumentCaptor<ByteBuffer> captor4 = ArgumentCaptor.forClass (ByteBuffer.class);
      verify( handler ).handleResponse (captor1.capture (), captor2.capture (), captor3.capture (), captor4.capture ());

      // Verify that the passed InetSocketAddress had the expected port
      assertEquals (1234, captor1.getValue ().getPort ());

      // Verify that the transport reference was passed as expected
      assertEquals( transport, captor2.getValue ());

      // Verify all the values in the supplied header
      assertEquals (0x000F, captor3.getValue ().command);
      assertEquals (0x0004, captor3.getValue ().payloadSize);
      assertEquals (0x0005, captor3.getValue ().dataType);
      assertEquals (0x0001, captor3.getValue ().dataCount);
      assertEquals (0xDEADBEEF, captor3.getValue ().parameter1);
      assertEquals (0xDABBAD00, captor3.getValue ().parameter2);

      // Verify that the first byte in the supplied Bytebuffer is the first
      // byte in the header (which is our command).
      assertEquals (0x000F, captor4.getValue ().getShort (0));

      // Verify that the buffer also contains the payload data.
      // Note: payload data starts at offset 16 because the header occupies
      // the earlier space in the buffer
      assertEquals (0xCAFEBABE, captor4.getValue ().getInt (16));

      // Verify no further interactions take place
      verifyNoMoreInteractions (handler);
   }

   @Test
   void testCaCommandWithPayload_HeaderSuppliedInTwoChunks() throws IOException
   {
      // We will use CA_PROTO_READ_NOTIFY with an element count of 1 and a float payload as an example
      final short cmdVersion = 0x000F;
      final short payloadSize = 0x0004; // DBR_FLOAT has a payload size of 4 bytes
      final short dataType = 0x0002;    // DBR_FLOAT ID
      final short dataCount = 0x0001;   // must be 1
      final int param1 = 0xDEADBEEF;     // SID
      final int param2 = 0xDABBAD00;     // IOID

      final ByteBuffer bufSocketRead1 = (ByteBuffer) ByteBuffer.allocate (8)
            .putShort (cmdVersion)
            .putShort (payloadSize)
            .putShort (dataType)
            .putShort (dataCount)
            .flip ();

      final ByteBuffer bufSocketRead2 = (ByteBuffer) ByteBuffer.allocate (8)
            .putInt (param1)
            .putInt (param2)
            .flip ();

      final ByteBuffer bufSocketRead3 = (ByteBuffer) ByteBuffer.allocate (4)
            .putFloat (1234.5678f)
            .flip ();

      // Create the mocking behaviour which passes our prepared buffers to the
      // object under test when it reads the channel
      Mockito.when (channel.read (ArgumentMatchers.<ByteBuffer> any ())).thenAnswer (i -> {
         // The first time the channel is read return our command.
         final ByteBuffer suppliedBuf = i.getArgument (0);
         suppliedBuf.put (bufSocketRead1);
         return 8;
      }).thenAnswer (i -> {
         // The first time the channel is read return our command.
         final ByteBuffer suppliedBuf = i.getArgument (0);
         suppliedBuf.put (bufSocketRead2);
         return 8;
      }).thenAnswer (i -> {
         // The second time our channel is read return the payload
         final ByteBuffer suppliedBuf = i.getArgument (0);
         suppliedBuf.put (bufSocketRead3);
         return 4;
      }).thenAnswer (i -> {
         // The third time our channel is read return an indicator showing
         // that there are no further bytes to be read.
         return 0;
      });

      // Create the mocking behaviour which ensures that a selection event
      // triggers the socket read handling
      final SelectionKey selectionKey = Mockito.mock (SelectionKey.class);
      Mockito.when( selectionKey.isValid ()).thenReturn (true);
      Mockito.when( selectionKey.readyOps ()).thenReturn (1);

      // Go ahead and trigger the processing which reads data from the socket.
      transport.handleEvent (selectionKey);

      // Now verify our expectation that the handler class gets called with the expected arguments
      ArgumentCaptor<InetSocketAddress> captor1 = ArgumentCaptor.forClass (InetSocketAddress.class);
      ArgumentCaptor<Transport> captor2 = ArgumentCaptor.forClass (Transport.class);
      ArgumentCaptor<Header> captor3 = ArgumentCaptor.forClass (Header.class);
      ArgumentCaptor<ByteBuffer> captor4 = ArgumentCaptor.forClass (ByteBuffer.class);
      verify( handler ).handleResponse (captor1.capture (), captor2.capture (), captor3.capture (), captor4.capture ());

      // Verify that the passed InetSocketAddress had the expected port
      assertEquals(1234, captor1.getValue ().getPort ());

      // Verify that the transport reference was passed as expected
      assertEquals (transport, captor2.getValue ());

      // Verify all the values in the supplied header
      assertEquals(0x000F, captor3.getValue ().command);
      assertEquals(0x0004, captor3.getValue ().payloadSize);
      assertEquals(0x0002, captor3.getValue ().dataType);
      assertEquals(0x0001, captor3.getValue ().dataCount);
      assertEquals(0xDEADBEEF, captor3.getValue ().parameter1);
      assertEquals(0xDABBAD00, captor3.getValue ().parameter2);

      // Verify that the first byte in the supplied Bytebuffer is the first
      // byte in the header (which is our command).
      assertEquals(0x000F, captor4.getValue ().getShort (0));

      // Verify that the buffer also contains the payload data.
      // Note: payload data starts at offset 16 because the header occupies
      // the earlier space in the buffer
      assertEquals(1234.5678f, captor4.getValue ().getFloat (16));

      // Verify no further interactions take place
      verifyNoMoreInteractions( handler );
   }

   @Test
   void testCaCommandWithPayload_HeaderAndPayloadSplitDifferently() throws IOException
   {
      // We will use CA_PROTO_READ_NOTIFY with an element count of 1 and a long payload as an example
      final short cmdVersion = 0x000F;
      final short payloadSize = 0x0004; // DBR_LONG has a payload size of 4 bytes
      final short dataType = 0x0005;    // DBR_LONG ID
      final short dataCount = 0x0001;   // must be 1
      final int param1 = 0xDEADBEEF;     // SID
      final int param2 = 0xDABBAD00;     // IOID

      final ByteBuffer bufSocketRead1 = (ByteBuffer) ByteBuffer.allocate (12)
            .putShort (cmdVersion)
            .putShort (payloadSize)
            .putShort (dataType)
            .putShort (dataCount)
            .putInt (param1)
            .flip ();

      final ByteBuffer bufSocketRead2 = (ByteBuffer) ByteBuffer.allocate (8)
            .putInt (param2)
            .putInt (0xCAFEBABE)
            .flip ();

      // Create the mocking behaviour which passes our prepared hdrBuffer to the
      // object under test when it reads the channel
      Mockito.when (channel.read (ArgumentMatchers.<ByteBuffer> any ())).thenAnswer (i -> {
         // The first time the channel is read return our command.
         final ByteBuffer suppliedBuf = i.getArgument (0);
         suppliedBuf.put (bufSocketRead1);
         return 16;
      }).thenAnswer (i -> {
         // The second time our channel is read return the payload
         final ByteBuffer suppliedBuf = i.getArgument (0);
         suppliedBuf.put (bufSocketRead2);
         return 4;
      }).thenAnswer (i -> {
         // The third time our channel is read return an indicator showing
         // that there are no further bytes to be read.
         return 0;
      });

      // Create the mocking behaviour which ensures that a selection event
      // triggers the socket read handling
      final SelectionKey selectionKey = Mockito.mock (SelectionKey.class);
      Mockito.when (selectionKey.isValid ()).thenReturn (true);
      Mockito.when (selectionKey.readyOps ()).thenReturn (1);

      // Go ahead and trigger the processing which reads data from the socket.
      transport.handleEvent( selectionKey );

      // Now verify our expectation that the handler class gets called with the expected arguments
      final ArgumentCaptor<InetSocketAddress> captor1 = ArgumentCaptor.forClass (InetSocketAddress.class);
      final ArgumentCaptor<Transport> captor2 = ArgumentCaptor.forClass (Transport.class);
      final ArgumentCaptor<Header> captor3 = ArgumentCaptor.forClass (Header.class);
      final ArgumentCaptor<ByteBuffer> captor4 = ArgumentCaptor.forClass (ByteBuffer.class);
      verify (handler).handleResponse (captor1.capture (), captor2.capture (), captor3.capture (), captor4.capture ());

      // Verify that the passed InetSocketAddress had the expected port
      assertEquals(1234, captor1.getValue ().getPort ());

      // Verify that the transport reference was passed as expected
      assertEquals( transport, captor2.getValue ());

      // Verify all the values in the supplied header
      assertEquals(0x000F, captor3.getValue ().command);
      assertEquals(0x0004, captor3.getValue ().payloadSize);
      assertEquals(0x0005, captor3.getValue ().dataType);
      assertEquals(0x0001, captor3.getValue ().dataCount);
      assertEquals(0xDEADBEEF, captor3.getValue ().parameter1);
      assertEquals(0xDABBAD00, captor3.getValue ().parameter2);

      // Verify that the first byte in the supplied Bytebuffer is the first
      // byte in the header (which is our command).
      assertEquals (0x000F, captor4.getValue ().getShort (0));

      // Verify that the buffer also contains the payload data.
      // Note: payload data starts at offset 16 because the header occupies
      // the earlier space in the buffer
      assertEquals (0xCAFEBABE, captor4.getValue ().getInt (16));

      // Verify no further interactions take place
      verifyNoMoreInteractions (handler);
   }

   @MethodSource( "getArgumentsForCaLatencyTest" )
   @ParameterizedTest
   void testCaReadLatency( int maximumExecutionTimeInMicroseconds) throws IOException
   {
      // We will use CA_PROTO_READ_NOTIFY with an element count of 1 and a long payload as an example
      final short cmdVersion = 0x000F;
      final short payloadSize = 0x0004; // DBR_LONG has a payload size of 4 bytes
      final short dataType = 0x0005;    // DBR_LONG ID
      final short dataCount = 0x0001;   // must be 1
      final int param1 = 0xDEADBEEF;    // SID
      final int param2 = 0xDABBAD00;    // IOID

      final ByteBuffer bufSocketRead1 = (ByteBuffer) ByteBuffer.allocate( 20 )
            .putShort(cmdVersion)
            .putShort(payloadSize)
            .putShort(dataType)
            .putShort(dataCount)
            .putInt(param1)
            .putInt(param2)
            .putInt(0xCAFEBABE)
            .flip();

      // Create the mocking behaviour which passes our prepared buffers to the
      // object under test when it reads the channel
      Mockito.when(channel.read(ArgumentMatchers.<ByteBuffer> any())).thenAnswer(i -> {
         // The first time the channel is read return the buffer containing the header
         final ByteBuffer suppliedBuf = i.getArgument(0);
         suppliedBuf.put(bufSocketRead1);
         return 20;
      }).thenAnswer(i -> {
         // The second time our channel is read return an indicator showing
         // that there are no further bytes to be read.
         return 0;
      });

      // Create the mocking behaviour which ensures that a selection event
      // triggers the socket read handling
      final SelectionKey selectionKey = Mockito.mock(SelectionKey.class);
      Mockito.when(selectionKey.isValid()).thenReturn(true);
      Mockito.when(selectionKey.readyOps()).thenReturn(1);

      // Now verify our expectation that the handler class gets called with the expected arguments
      final ArgumentCaptor<InetSocketAddress> captor1 = ArgumentCaptor.forClass(InetSocketAddress.class);
      final ArgumentCaptor<Transport> captor2 = ArgumentCaptor.forClass(Transport.class);
      final ArgumentCaptor<Header> captor3 = ArgumentCaptor.forClass(Header.class);
      final ArgumentCaptor<ByteBuffer> captor4 = ArgumentCaptor.forClass(ByteBuffer.class);

      // Go ahead and trigger the processing which reads data from the socket.
      final StopWatch s = StopWatch.createStarted();
      transport.handleEvent( selectionKey );
      final long elapsedTimeInMicroseconds = s.getTime(TimeUnit.MICROSECONDS);
      logger.log( Level.INFO,String.format( "Transport latency time was '%.3f' ", (float) elapsedTimeInMicroseconds / 1000) + "ms" );

      verify( handler ).handleResponse( captor1.capture(), captor2.capture(), captor3.capture(), captor4.capture() );

      // Verify that the passed InetSocketAddress had the expected port
      assertEquals(1234, captor1.getValue().getPort());

      // Verify that the transport reference was passed as expected
      assertEquals(transport, captor2.getValue());

      // Verify all the values in the supplied header
      assertEquals(0x000F, captor3.getValue().command);
      assertEquals(0x0004, captor3.getValue().payloadSize);
      assertEquals(0x0005, captor3.getValue().dataType);
      assertEquals(0x0001, captor3.getValue().dataCount);
      assertEquals(0xDEADBEEF, captor3.getValue().parameter1);
      assertEquals(0xDABBAD00, captor3.getValue().parameter2);

      // Verify that the first byte in the supplied Bytebuffer is the first
      // byte in the header (which is our command).
      assertEquals(0x000F, captor4.getValue().getShort(0));

      // Verify that the buffer also contains the payload data.
      // Note: payload data starts at offset 16 because the header occupies
      // the earlier space in the buffer
      assertEquals(0xCAFEBABE, captor4.getValue().getInt(16));

      // Verify no further interactions take place
      verifyNoMoreInteractions(handler);

      // Verify the timing was no greater than expected
      assertTrue(elapsedTimeInMicroseconds < maximumExecutionTimeInMicroseconds,
                 "Actual Execution Time was: "  + elapsedTimeInMicroseconds + " us. " +
                 "Maximum Execution Time was: " + maximumExecutionTimeInMicroseconds + " us." );
   }


/*- Private methods ----------------------------------------------------------*/

   // Note: the first test always runs slower.
   private static Stream<Arguments> getArgumentsForCaLatencyTest()
   {
      return Stream.of ( Arguments.of( 100_000 ),
                         Arguments.of( 25_000 ),
                         Arguments.of( 25_000 ),
                         Arguments.of( 25_000 ),
                         Arguments.of( 25_000 ),
                         Arguments.of( 25_000 ),
                         Arguments.of( 25_000 ) );
   }

/*- Nested Classes -----------------------------------------------------------*/

}

