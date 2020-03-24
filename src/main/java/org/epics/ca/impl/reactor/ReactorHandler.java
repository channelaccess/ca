package org.epics.ca.impl.reactor;

import java.nio.channels.SelectionKey;

/**
 * Reactor handler interface.
 */
public interface ReactorHandler
{

   /**
    * Process request of given <code>SelectionKey</code>.
    *
    * @param key key to be processed.
    */
   void handleEvent( SelectionKey key );

}
