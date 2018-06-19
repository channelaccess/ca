package org.epics.ca;

public interface Listener extends AutoCloseable
{

   // suppresses AutoCloseable.close() exception
   @Override
   void close();

}
