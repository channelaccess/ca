package org.epics.ca.util;

/**
 * Utility for dumping binary data.
 */
@SuppressWarnings( "UnnecessaryLocalVariable" )
public class HexDump
{

   /**
    * Output a buffer in hex format.
    *
    * @param name name (description) of the message.
    * @param bs   buffer to dump
    */
   public static void hexDump( String name, byte[] bs )
   {
      hexDump (name, bs, 0, bs.length);
   }

   /**
    * Output a buffer in hex format.
    *
    * @param name name (description) of the message.
    * @param bs   buffer to dump
    * @param len  first bytes (length) to dump.
    */
   public static void hexDump(
         String name,
         byte[] bs,
         int len
   )
   {
      hexDump (name, bs, 0, len);
   }

   /**
    * Output a buffer in hex format.
    *
    * @param name  name (description) of the message.
    * @param bs    buffer to dump
    * @param start dump message using given offset.
    * @param len   first bytes (length) to dump.
    */
   public static void hexDump(
         String name,
         byte[] bs,
         int start,
         int len
   )
   {
      hexDump (null, name, bs, start, len);
   }

   /**
    * Output a buffer in hex format.
    *
    * @param prologue string to prefixed to debug output, can be <code>null</code>
    * @param name     name (description) of the message.
    * @param bs       buffer to dump
    * @param start    dump message using given offset.
    * @param len      first bytes (length) to dump.
    */
   public static synchronized void hexDump(
         String prologue,
         String name,
         byte[] bs,
         int start,
         int len
   )
   {
      StringBuilder out;
      if ( prologue == null )
         out = new StringBuilder ("Hexdump [" + name + "] size = " + len);
      else
         out = new StringBuilder (prologue + "\nHexdump [" + name + "] size = " + len);

      StringBuffer chars = new StringBuffer ();

      for ( int i = start; i < (start + len); i++ )
      {
         if ( ((i - start) % 16) == 0 )
         {
            out.append (chars);
            out.append ('\n');
            chars = new StringBuffer ();
         }

         chars.append (toAscii (bs[ i ]));

         out.append (toHexAndSpace (bs[ i ]));

         if ( ((i - start) % 4) == 3 )
         {
            chars.append (' ');
            out.append (' ');
         }
      }

      if ( len % 16 != 0 )
      {
         final int delta_bytes = 16 - (len % 16);

         //rest of line (no of bytes)
         //each byte takes two chars plus one ws
         int pad = delta_bytes * 3;

         //additional whitespaces after four bytes
         pad += (delta_bytes / 4);

         for ( int i = 0; i < pad; i++ )
         {
            chars.insert (0, ' ');
         }
      }

      out.append (chars);
      System.out.println (out);
   }

   /**
    * byte to hexchar mapping.
    */
   private static final char[] lookup =
         new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

   /**
    * Get hex representation of byte.
    *
    * @param b the byte to convert.
    * @return string hex representation of byte.
    */
   private static String toHexAndSpace( byte b )
   {
      final int upper = (b >> 4) & 0x0F;
      final int lower = b & 0x0F;
      final String sb = String.valueOf( lookup[ upper ]) + lookup[ lower ] + ' ';
      return sb;
   }

   /**
    * Get hex representation of byte.
    *
    * @param b the byte to convert.
    * @return string hex representation of byte.
    */
   public static String toHex( byte b )
   {
      final int upper = (b >> 4) & 0x0F;
      final int lower = b & 0x0F;
      final String sb = String.valueOf(lookup[ upper ]) +  lookup[ lower ];
      return sb;
   }

   /**
    * Get ASCII representation of byte, dot if non-readable.
    *
    * @param b the byte to convert.
    * @return ASCII representation of byte, dot if non-readable.
    */
   public static char toAscii( byte b )
   {
      if ( b > (byte) 31 && b < (byte) 127 )
      {
         return (char) b;
      }
      else
      {
         return '.';
      }
   }
}
