package org.epics.ca.impl;

/**
 * Client (user) of the transport.
 */
public interface TransportClient
{

   /**
    * Notification of forcefully closed transport.
    */
   public void transportClosed();

}
