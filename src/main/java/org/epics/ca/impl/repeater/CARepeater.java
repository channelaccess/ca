package org.epics.ca.impl.repeater;

import java.io.File;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.epics.ca.Constants;
import org.epics.ca.util.net.InetAddressUtil;

/**
 * CA repeater.
 */
@SuppressWarnings( { "rawtypes", "unchecked" } )
public class CARepeater implements Runnable
{

   // Get Logger
   private static final Logger logger = Logger.getLogger (CARepeater.class.getName ());

   static
   {
      // force only IPv4 sockets, since EPICS does not work right with IPv6 sockets
      // see http://java.sun.com/j2se/1.5.0/docs/guide/net/properties.html
      System.setProperty ("java.net.preferIPv4Stack", "true");
   }

   /**
    * System JVM property key to force native repeater.
    */
   public static final String CA_FORCE_NATIVE_REPEATER = "CA_FORCE_NATIVE_REPEATER";

   /**
    * System JVM property key to disable CA repeater.
    */
   public static final String CA_DISABLE_REPEATER = "CA_DISABLE_REPEATER";

   /**
    * CA repeater client.
    */
   class Client
   {

      /**
       * Client address.
       */
      private InetSocketAddress clientAddress;

      /**
       * Client address.
       */
      private DatagramSocket clientSocket = null;

      /**
       * Constructor.
       *
       * @param clientAddress
       */
      public Client( InetSocketAddress clientAddress )
      {
         this.clientAddress = clientAddress;
      }

      /**
       * Connect.
       *
       * @return success flag.
       */
      public boolean connect()
      {
         try
         {
            clientSocket = createDatagramSocket ();
            clientSocket.connect (clientAddress);
         }
         catch ( Throwable th )
         {
            // failed to connect
            logger.log (Level.FINEST, "Failed to connect to: " + clientAddress, th);
            return false;
         }
         return true;
      }

      /**
       * Destroy client (close socket).
       */
      public void destroy()
      {
         if ( clientSocket != null )
            clientSocket.close ();
      }

      /**
       * Client address.
       *
       * @return client address.
       */
      public InetSocketAddress getClientAddress()
      {
         return clientAddress;
      }

      /**
       * Verify the state of the socket.
       *
       * @return verification success.
       */
      public boolean verify()
      {
         try
         {
            // this should fail, if client is listening
            DatagramSocket socket = createDatagramSocket (clientAddress.getPort (), false);
            socket.close ();
            logger.log (Level.FINEST, "Dead client detected: " + clientAddress);
            return false;
         }
         catch ( Throwable th )
         {
            // this is OK
            return true;
         }
      }

      /**
       * Send packet.
       *
       * @param packet
       * @return success status.
       */
      public boolean send( DatagramPacket packet )
      {
         packet.setSocketAddress (clientAddress);
         try
         {
            logger.log (Level.FINEST, "Sending packet to: " + clientAddress);
            clientSocket.send (packet);
         }
         catch ( Throwable th )
         {
            // failed to send
            logger.log (Level.FINEST, "Failed to send packet to: " + clientAddress, th);
            return false;
         }
         return true;
      }

      /**
       * Send repeater confirm message.
       *
       * @return confirmation send success status.
       */
      public boolean sendConfirm()
      {
         // build REPEATER_CONFIRM message
         byte[] message = new byte[ Constants.CA_MESSAGE_HEADER_SIZE ];
         ByteBuffer buffer = ByteBuffer.wrap (message);
         buffer.putShort (COMMAND_OFFSET, REPEATER_CONFIRM);
         buffer.putInt (AVAILABLE_OFFSET, InetAddressUtil.ipv4AddressToInt (clientAddress.getAddress ()));

         // send
         DatagramPacket packet = new DatagramPacket (message, message.length);
         return send (packet);
      }
   }

   // message field codes
   private static final int COMMAND_OFFSET = 0;
   private static final int AVAILABLE_OFFSET = 12;

   // message command codes
   private static final short CA_PROTO_VERSION = 0;
   private static final short REPEATER_REGISTER = 24;
   private static final short REPEATER_CONFIRM = 17;
   private static final short CA_PROTO_RSRV_IS_UP = 13;

   /**
    * Repeater port.
    */
   protected int repeaterPort = Constants.CA_REPEATER_PORT;

   /**
    * Local unbounded DatagramSocket.
    */
   protected DatagramSocket localDatagramSocket = null;

   /**
    * List of registered clients.
    */
   protected List<Client> clients = new ArrayList<Client> ();

   /**
    * Constructor.
    */
   public CARepeater()
   {
      // read configuration, repeater port
      String port = System.getProperty ("EPICS_CA_REPEATER_PORT");
      if ( port != null )
      {
         try
         {
            repeaterPort = Integer.parseInt (port);
         }
         catch ( NumberFormatException nfe )
         {
            logger.log (Level.FINE, "Failed to parse repeater port '" + port + "'.", nfe);
         }
      }
   }

   /**
    * Constructor.
    *
    * @param repeaterPort repeater port.
    */
   public CARepeater( int repeaterPort )
   {
      this.repeaterPort = repeaterPort;
   }

   /**
    * @see java.lang.Runnable#run()
    */
   public void run()
   {
      process ();
   }

   protected void registerNewClient( InetSocketAddress clientAddress )
   {
      logger.fine ("Registering client: " + clientAddress);

      final int INADDR_LOOPBACK = 0x7F000001;
      if ( InetAddressUtil.ipv4AddressToInt (clientAddress.getAddress ()) != INADDR_LOOPBACK )
      {
         // create local datagram socket
         if ( localDatagramSocket == null )
         {
            try
            {
               localDatagramSocket = createDatagramSocket ();
            }
            catch ( Throwable th )
            {
               logger.log (Level.FINEST, "Failed to create local test datagram socket.", th);
            }
         }

         // try to bind it to a unbounded clientAddress, if it is local it will succeed
         if ( localDatagramSocket != null )
         {
            final int PORT_ANY = 0;

            try
            {
               // try...
               localDatagramSocket.bind (new InetSocketAddress (clientAddress.getAddress (), PORT_ANY));

               // close on success
               // multiple bounds not allowed by Java, so we will force recreate
               localDatagramSocket.close ();
               localDatagramSocket = null;

            }
            catch ( Throwable th )
            {
               // failed to connect, reject remote client
               return;
            }

         }
         else
         {
            // failed to do the test, reject remote (assumed) client
            return;
         }
      }

      Client client = null;
      // check if already registered
      synchronized ( clients )
      {
         // do not waste resources, if nobody to send
         if ( clients.size () != 0 )
         {
            Iterator<Client> iter = clients.iterator ();
            while ( iter.hasNext () )
            {
               Client c = iter.next ();
               if ( c.getClientAddress ().getPort () == clientAddress.getPort () )
               {
                  client = c;
                  break;
               }
            }
         }
      }

      boolean newClient = false;

      // create new, if necessary
      if ( client == null )
      {
         client = new Client (clientAddress);
         if ( !client.connect () )
         {
            client.destroy ();
            return;
         }

         // add
         synchronized ( clients )
         {
            clients.add (client);
         }

         newClient = true;
      }

      // send repeater confirm
      if ( !client.sendConfirm () )
      {
         // add
         synchronized ( clients )
         {
            clients.remove (client);
         }
         client.destroy ();
      }

      logger.fine ("Client registered: " + clientAddress);

      // send noop message to all other clients, not to accumulate clients
      // when there are no beacons
      byte[] message = new byte[ Constants.CA_MESSAGE_HEADER_SIZE ];
      ByteBuffer buffer = ByteBuffer.wrap (message);
      buffer.putShort (COMMAND_OFFSET, CA_PROTO_VERSION);
      fanOut (clientAddress, buffer);

      // verify all clients
      if ( newClient )
         verifyClients ();

   }

   protected void fanOut( InetSocketAddress fromAddress, ByteBuffer buffer )
   {
      synchronized ( clients )
      {
         // do not waste resources, if nobody to send
         if ( clients.size () == 0 )
            return;

         // create packet to send, send address still needs to be set
         DatagramPacket packetToSend =
               new DatagramPacket (buffer.array (), buffer.position (), buffer.limit ());

         Iterator<Client> iter = clients.iterator ();
         while ( iter.hasNext () )
         {
            Client client = iter.next ();

            // don't reflect back to sender
            if ( client.getClientAddress ().equals (fromAddress) )
               continue;

            // send, send, send...
            if ( !client.send (packetToSend) )
            {
               // check if socket is valid
               if ( !client.verify () )
               {
                  // destory and remove
                  client.destroy ();
                  iter.remove ();
               }
            }

         }
      }
   }

   /**
    * Verify all the clients.
    */
   protected void verifyClients()
   {
      synchronized ( clients )
      {
         // do not waste resources, if nobody to send
         if ( clients.size () == 0 )
            return;

         Iterator<Client> iter = clients.iterator ();
         while ( iter.hasNext () )
         {
            Client client = iter.next ();

            // check if socket is valid
            if ( !client.verify () )
            {
               // destory and remove
               client.destroy ();
               iter.remove ();
            }
         }
      }
   }

   /**
    * Process UDP requests.
    */
   protected void process()
   {
      DatagramSocket socket = null;
      try
      {
         logger.fine ("Initializing CA repeater.");

         // Create a buffer to read datagrams into. If a packet is
         // larger than this buffer, the excess will simply be discarded.
         byte[] buffer = new byte[ Constants.MAX_UDP_RECV ];
         ByteBuffer data = ByteBuffer.wrap (buffer);

         // create a packet to receive data into the buffer
         DatagramPacket packet = new DatagramPacket (buffer, buffer.length);

         // create and bind datagram socket
         try
         {
            socket = createDatagramSocket (repeaterPort, true);
         }
         catch ( BindException be )
         {
            // notrify and finish
            logger.log (Level.FINE, "Failed to bind.", be);
            return;
         }
         logger.fine ("Binded to UDP socket: " + socket.getLocalSocketAddress ());

         logger.fine ("CA repeater attached and initialized.");

         while ( true )
         {
            // wait to receive a datagram
            data.clear ();
            socket.receive (packet);

            InetSocketAddress receivedFrom = (InetSocketAddress) packet.getSocketAddress ();

            int bytesReceived = packet.getLength ();
            data.limit (bytesReceived);
            if ( bytesReceived >= Constants.CA_MESSAGE_HEADER_SIZE )
            {
               short command = data.getShort (COMMAND_OFFSET);
               // register request message
               if ( command == REPEATER_REGISTER )
               {
                  registerNewClient (receivedFrom);

                  // skip this header, process rest if any left
                  data.position (Constants.CA_MESSAGE_HEADER_SIZE);
                  if ( !data.hasRemaining () )
                     continue;
               }
               // beacon
               else if ( command == CA_PROTO_RSRV_IS_UP )
               {
                  // set address, if missing
                  short address = data.getShort (AVAILABLE_OFFSET);
                  if ( address == 0 )
                  {
                     data.putInt (AVAILABLE_OFFSET,
                                  InetAddressUtil.ipv4AddressToInt (packet.getAddress ()));
                  }
               }
            }
            // empty message request registers too
            else if ( bytesReceived == 0 )
            {
               registerNewClient (receivedFrom);
               continue;
            }

            // fan out packet
            fanOut (receivedFrom, data);
         }

      }
      catch ( Throwable th )
      {
         logger.log (Level.SEVERE, "Unexpected exception caught.", th);
      }
      finally
      {
         if ( socket != null )
            socket.close ();
      }
   }

   /**
    * Constructs an unbound datagram socket.
    *
    * @return default unbound datagram socket.
    * @throws SocketException socket exception
    */
   protected static DatagramSocket createDatagramSocket()
         throws SocketException
   {
      return new DatagramSocket (null);
   }

   /**
    * Constructs a atagram socket bound to the wildcard address on defined port.
    *
    * @param port         port
    * @param reuseAddress reuse address
    * @return default bounded datagram socket.
    * @throws SocketException socket exception
    */
   protected static DatagramSocket createDatagramSocket( int port, boolean reuseAddress )
         throws SocketException
   {
      DatagramSocket socket = new DatagramSocket (null);

      socket.bind (new InetSocketAddress (port));
      socket.setReuseAddress (reuseAddress);

      return socket;
   }

   /**
    * Check if repeater is running.
    *
    * @param repeaterPort repeater port.
    * @return <code>true</code> if repeater is already running, <code>false</code> otherwise
    */
   protected static boolean isRepeaterRunning( int repeaterPort )
   {
      // test if repeater is already running, by binding to its port
      try
      {
         DatagramSocket socket = createDatagramSocket (repeaterPort, true);
         socket.close ();
         // bind succeeded, repeater not running
         return false;
      }
      catch ( BindException be )
      {
         // bind failed, socket in use
         return true;
      }
      catch ( SocketException se )
      {
         // Win7 "version" of: bind failed, socket in use
         return true;
      }
      catch ( Throwable th )
      {
         // unexpected error
         logger.log (Level.WARNING, "", th);
         return false;
      }
   }

   /**
    * Start repeater as detached process.
    * First checks if repeater is already running, if not
    * other JVM process is run.
    *
    * @param repeaterPort repeater port.
    * @throws Throwable throwable
    */
   public static void startRepeater( final int repeaterPort ) throws Throwable
   {
      // disable repeater check
      if ( System.getProperties ().containsKey (CA_DISABLE_REPEATER) )
         return;

      // force native repeater check
      if ( System.getProperties ().containsKey (CA_FORCE_NATIVE_REPEATER) )
      {
         JNIRepeater.repeaterInit ();
         return;
      }

      if ( repeaterPort <= 0 )
         throw new IllegalArgumentException ("port must be > 0");

      // nothing to do, if repeater is already running
      if ( isRepeaterRunning (repeaterPort) )
         return;

      PrivilegedAction action = new PrivilegedAction ()
      {

         public Object run()
         {

            // java.home java.class.path
            final String[] commandLine = new String[] {
                  System.getProperty ("java.home") + File.separator + "bin" + File.separator + "java",
                  "-classpath",
                  System.getProperty ("java.class.path"),
                  CARepeater.class.getName (),
                  "-p",
                  String.valueOf (repeaterPort)
            };

            try
            {
               Runtime.getRuntime ().exec (commandLine);
            }
            catch ( Throwable th )
            {
               System.err.println ("Failed to exec '" + commandLine[ 0 ] + "', trying to start native repeater...");
               logger.log (Level.SEVERE, "Failed to exec '" + commandLine[ 0 ] + "', trying to start native repeater...", th);

               try
               {
                  //  fallback: try to run native repeater
                  JNIRepeater.repeaterInit ();
               }
               catch ( Throwable th2 )
               {
                  System.err.println ("Failed to start native repeater.");
                  logger.log (Level.SEVERE, "Failed to start native repeater.", th);
               }
            }

            return null;
         }
      };

      Object res = AccessController.doPrivileged (action);
      if ( res != null )
         throw new Exception ("Unable to init CA Repeater", (Throwable) res);

   }


   /**
    * Main entry-point.
    *
    * @param argv arguments.
    */
   public static void main( String argv[] )
   {
      CARepeater repeater;

      // check for port argument
      int port = -1;
      if ( argv.length >= 2 &&
            (argv[ 0 ].equals ("-p") || argv[ 0 ].equals ("--port")) )
      {
         try
         {
            port = Integer.parseInt (argv[ 1 ]);
         }
         catch ( NumberFormatException nfe )
         {
            System.err.println ("Failed to parse repeater port '" + argv[ 1 ] + "'.");
         }
      }

      // create repeater
      if ( port > 0 )
         repeater = new CARepeater (port);
      else
         repeater = new CARepeater ();

      // run, run, run...
      repeater.run ();
   }


   public static class JNIRepeater
   {

      /**
       * System JVM property key to disable JNI repeater.
       */
      public static final String JNI_DISABLE_REPEATER = "JNI_DISABLE_REPEATER";

      static public void repeaterInit()
      {

         if ( System.getProperties ().containsKey (JNI_DISABLE_REPEATER) )
            return;

         PrivilegedAction action = new PrivilegedAction ()
         {

            public Object run()
            {
               try
               {

                  String targetArch = JNIRepeater.getTargetArch ();

                  // read JNI native config
                  File caRepeaterPath =
                        new File (System.getProperty ("org.epics.ca." + targetArch + ".caRepeater.path", ""));

                  try
                  {
                     String caRepeater = "caRepeater";
                     if ( caRepeaterPath.exists () )
                     {
                        caRepeater = (new File (caRepeaterPath, "caRepeater")).getAbsolutePath ();
                     }
                     Runtime.getRuntime ().exec (caRepeater);
                  }
                  catch ( java.io.IOException ex )
                  {
                     Runtime.getRuntime ().exec ("caRepeater");
                  }

               }
               catch ( Throwable ex2 )
               {
                  // noop
               }

               return null;
            }
         };

         Object res = AccessController.doPrivileged (action);
         if ( res != null )
            throw new RuntimeException ("Unable to init JNI CA Repeater", (Throwable) res);
      }


      /**
       * Get standard "system-arch" string.
       *
       * @return standard "system-arch" string.
       */
      static public String getTargetArch()
      {

         String osname = System.getProperty ("os.name", "");

         float osversion = 0;
         try
         {
            osversion = NumberFormat.getInstance ().parse (System.getProperty ("os.version", "")).floatValue ();
         }
         catch ( ParseException pe )
         {
            // noop
         }

         String osarch = System.getProperty ("os.arch", "");

         if ( osarch.equals ("i386")
               || osarch.equals ("i486")
               || osarch.equals ("i586") )
         {
            osarch = "x86";
         }

         if ( osname.equals ("SunOS") )
         {
            if ( osversion >= 5 )
            {
               if ( osarch.equals ("sparc") )
               {
                  return "solaris-sparc";
               }
               else if ( osarch.equals ("x86") )
               {
                  return "solaris-x86";
               }
            }
         }
         else if ( osname.equals ("Linux") )
         {
            if ( osarch.equals ("x86") )
            {
               return "linux-x86";
            }
         }
         else if ( osname.equals ("Mac OS X") )
         {
            return "darwin-ppc";
         }
         else if ( osname.startsWith ("Win") )
         {
            return "win32-x86";
         }
         return "unknown";
      }

   }

}
