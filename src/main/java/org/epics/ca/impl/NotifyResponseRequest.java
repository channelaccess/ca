package org.epics.ca.impl;

import java.nio.ByteBuffer;

/**
 * ResponseRequest expecting data response.
 */
public interface NotifyResponseRequest extends ResponseRequest
{

   /**
    * Notification response.
    *
    * @param status the CA status code.
    * @param dataType the CA data type.
    * @param dataCount the CA channel element count.
    * @param dataPayloadBuffer the buffer with the payload
    */
   void response( int status, short dataType, int dataCount, ByteBuffer dataPayloadBuffer );

}
