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
   public InetSocketAddress getRemoteAddress();

   /**
    * Get context transport is living in.
    *
    * @return context transport is living in.
    */
   public ContextImpl getContext();

   /**
    * Transport protocol minor revision.
    *
    * @return protocol minor revision.
    */
   public short getMinorRevision();

   public int getPriority();

   public ByteBuffer acquireSendBuffer( int requiredSize );

   public void releaseSendBuffer( boolean ignore, boolean flush );

   public void flush();

}
