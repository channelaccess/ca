/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import java.util.Properties;

import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.ContextImpl;
import org.epics.ca.impl.ProtocolConfiguration;

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
    * Create an instance whose channel-access protocol configuration is based on
    * the values of operating system environmental variables, the values of the
    * the Java system properties, or twhen not otherwise specified, the library
    * defaults.
    */
   public Context()
   {
      this( System.getProperties() );
   }

   /**
    * Create an instance whose channel-access protocol configuration is based on
    * the values of operating system environmental variables, the values of
    * the supplied properties object, or when not otherwise specified, the
    * library defaults.
    *
    * @param properties an object whose definitions may override the
    *   values set in the operating system environment.
    * @throws NullPointerException if the properties argument was null.
    */
   public Context( Properties properties )
   {
      Validate.notNull( properties, "null properties" );
      final ProtocolConfiguration protocolConfiguration = new ProtocolConfiguration( properties );
      delegate = new ContextImpl( protocolConfiguration );
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * Creates a new channel of the specified name and type and with the
    * default priority.
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
    * @return the channel.
    *
    * @throws NullPointerException if the channel name was null.
    * @throws NullPointerException if the channel type was null.
    * @throws IllegalArgumentException if the channel name was an empty string.
    * @throws IllegalArgumentException if the channel name was of an unreasonable length.
    * @throws IllegalArgumentException if the channel type was invalid.
    * @throws IllegalStateException if the context was already closed.
    */
   public <T> Channel<T> createChannel( String channelName, Class<T> channelType )
   {
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
    *
    * @param <T> the type parameter.
    * @param priority the priority to be registered with the channel access server.
    * @return the channel.
    *
    * @throws NullPointerException if the channel name was null.
    * @throws NullPointerException if the channel type was null.
    * @throws IllegalArgumentException if the channel name was an empty string.
    * @throws IllegalArgumentException if the channel name was of an unreasonable length.
    * @throws IllegalArgumentException if the channel type was invalid.
    * @throws IllegalArgumentException if the priority was outside the allowed range.
    * @throws IllegalStateException if the context was already closed.
    */
   public <T> Channel<T> createChannel( String channelName, Class<T> channelType, int priority )
   {
      Validate.notNull( channelName );
      Validate.notNull( channelType );
      return delegate.createChannel( channelName, channelType, priority);
   }

   /**
    * Closes the context, disposing of all underlying resources.
    */
   @Override
   public void close()
   {
      delegate.close ();
   }
}
