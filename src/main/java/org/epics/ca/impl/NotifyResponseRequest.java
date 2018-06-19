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
    * @param status
    * @param dataType
    * @param dataCount
    * @param dataPayloadBuffer
    */
   public void response( int status, short dataType, int dataCount, ByteBuffer dataPayloadBuffer );

}
