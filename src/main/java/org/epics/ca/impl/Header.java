package org.epics.ca.impl;

import java.nio.ByteBuffer;

public class Header
{
   /**
    * Command field of the header.
    */
   public short command;

   /**
    * Payload size field of the header.
    */
   public int payloadSize;

   /**
    * Data type field of the header.
    */
   public short dataType;

   /**
    * Data count field of the header.
    * NOTE: extended (unsigned short -&gt; int)
    */
   public int dataCount;

   /**
    * Parameter 1 field of the header.
    */
   public int parameter1;

   /**
    * Parameter 2 field of the header.
    */
   public int parameter2;


   /**
    * Parse CA response header.
    *
    * @param headerBuffer response header to be parsed, condition: headerBuffer.remaining() &gt;= CA_MESSAGE_HEADER_SIZE
    */
   public boolean read( ByteBuffer headerBuffer )
   {
      //
      // read fields
      //

      command = headerBuffer.getShort ();
      // signed short conversion -> signed int
      payloadSize = headerBuffer.getShort () & 0xFFFF;
      dataType = headerBuffer.getShort ();
      // signed short conversion -> signed int
      dataCount = headerBuffer.getShort () & 0xFFFF;
      parameter1 = headerBuffer.getInt ();
      parameter2 = headerBuffer.getInt ();

      // extended header
      if ( payloadSize == 0xFFFF )
      {
         if ( headerBuffer.remaining () < 8 )
            return false;

         /*
          * Because Java can't represent negative int as a 32 bit positive integer, it has to be promoted to a long:
          *  (1) Assign it to a long.
          *  (2) Clear the upper 32 bit of the long by logical AND with 0x00000000FFFFFFFF.
          *  (3) The resulting long is a positive number.
          *
          * Anyway, maximum buffer size is limited w/ Integer.MAX_VALUE,
          * so int type us used and values > Integer.MAX_VALUE are not supported.
          */
         payloadSize = headerBuffer.getInt ();
         dataCount = headerBuffer.getInt ();
      }

      return true;
   }
}
