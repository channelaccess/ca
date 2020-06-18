/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import java.util.Properties;

import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.ContextImpl;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class Context implements AutoCloseable
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private final ContextImpl delegate;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Creates a new context based on the default properties (that are overridable
    * by environment variables set on the local host).
    */
   public Context()
   {
      this( System.getProperties() );
   }

   /**
    * Creates a new context based on the default properties, that are overridable
    * by environment variables set on the local host, or by definitions passed
    * in the supplied properties object).
    *
    * @param properties the properties object
    */
   public Context( Properties properties )
   {
      Validate.notNull( properties, "null properties" );
      delegate = new ContextImpl( properties );
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * Creates a new channel of the specified name and type.
    *
    * @param channelName the name of the Channel (which should follow standard
    *   EPICS naming conventions).
    *
    * @param channelType the type of the channel which will determine the
    *    type used when communicating with the remote channel access server.
    *    Note: &lt;Object&gt; can be used to force the channel to use the
    *    native type on the server.
    *
    * @param <T> the type parameter.
    *
    * @return the channel.
    */
   public <T> Channel<T> createChannel( String channelName, Class<T> channelType )
   {
      Validate.notNull( channelName );
      Validate.notNull( channelType );

      return delegate.createChannel( channelName, channelType, Constants.CHANNEL_PRIORITY_DEFAULT );
   }

   /**
    * Creates a new channel of the specified name and type and with the specified priority.
    *
    * @param channelName the name of the Channel (which should follow standard
    *   EPICS naming conventions).
    *
    * @param channelType the type of the channel which will determine the
    *    type used when communicating with the remote channel access server.
    *    Note: &lt;Object&gt; can be used to force the channel to use the
    *    native type on the server.
    * @param <T> the type parameter.
    * @param priority the priority that should be informed to the channel access
    *    server during communcation.
    *
    * @return the channel.
    */
   public <T> Channel<T> createChannel( String channelName, Class<T> channelType, int priority )
   {
      return delegate.createChannel( channelName, channelType, priority);
   }

   /**
    * Closes the cintext disposing of all underlying resources.
    */
   @Override
   public void close()
   {
      delegate.close ();
   }

}
