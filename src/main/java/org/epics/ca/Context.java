/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import java.util.Properties;
import org.epics.ca.impl.ContextImpl;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class Context implements AutoCloseable
{

/*- Public attributes --------------------------------------------------------*/

   public enum Configuration
   {
      EPICS_CA_ADDR_LIST,
      EPICS_CA_AUTO_ADDR_LIST,
      EPICS_CA_CONN_TMO,
      EPICS_CA_BEACON_PERIOD,
      EPICS_CA_REPEATER_PORT,
      EPICS_CA_SERVER_PORT,
      EPICS_CA_MAX_ARRAY_BYTES
   }

/*- Private attributes -------------------------------------------------------*/

   private final ContextImpl delegate;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   public Context()
   {
      this( System.getProperties() );
   }

   public Context( Properties properties )
   {
      delegate = new ContextImpl( properties );
   }


/*- Public methods -----------------------------------------------------------*/

   public <T> Channel<T> createChannel( String channelName, Class<T> channelType )
   {
      return delegate.createChannel( channelName, channelType, Constants.CHANNEL_PRIORITY_DEFAULT );
   }

   public <T> Channel<T> createChannel( String channelName, Class<T> channelType, int priority )
   {
      return delegate.createChannel( channelName, channelType, priority);
   }

   @Override
   public void close()
   {
      delegate.close ();
   }

}
