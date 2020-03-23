package org.epics.ca.impl;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Interface defining transport (connection).
 */
public interface Transport
{

   /**
    * Get remote address.
    *
    * @return remote address.
    */
   InetSocketAddress getRemoteAddress();

   /**
    * Get context transport is living in.
    *
    * @return context transport is living in.
    */
   ContextImpl getContext();

   /**
    * Transport protocol minor revision.
    *
    * @return protocol minor revision.
    */
   short getMinorRevision();

   int getPriority();

   ByteBuffer acquireSendBuffer( int requiredSize );

   void releaseSendBuffer( boolean ignore, boolean flush );

   void flush();

}
