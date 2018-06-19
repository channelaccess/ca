package org.epics.ca;

import java.util.Properties;

import org.epics.ca.impl.ContextImpl;

public class Context implements AutoCloseable
{

   public enum Configuration
   {
      EPICS_CA_ADDR_LIST, EPICS_CA_AUTO_ADDR_LIST, EPICS_CA_CONN_TMO,
      EPICS_CA_BEACON_PERIOD, EPICS_CA_REPEATER_PORT, EPICS_CA_SERVER_PORT,
      EPICS_CA_MAX_ARRAY_BYTES
   }

   ;

   private final ContextImpl delegate;

   public Context()
   {
      this (System.getProperties ());
   }

   public Context( Properties properties )
   {
      delegate = new ContextImpl (properties);
   }

   public <T> Channel<T> createChannel( String channelName, Class<T> channelType )
   {
      return delegate.createChannel (channelName, channelType, Constants.CHANNEL_PRIORITY_DEFAULT);
   }

   public <T> Channel<T> createChannel( String channelName, Class<T> channelType, int priority )
   {
      return delegate.createChannel (channelName, channelType, priority);
   }

   @Override
   public void close()
   {
      delegate.close ();
   }

}
