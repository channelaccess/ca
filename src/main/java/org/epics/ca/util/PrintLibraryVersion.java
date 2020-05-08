package org.epics.ca.util;

import org.epics.ca.LibraryVersion;

public class PrintLibraryVersion
{

   public static void main( String[] args )
   {
      System.out.println( "Java CA v" + LibraryVersion.getAsString() );
   }

}
