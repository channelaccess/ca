package org.epics.ca.util;

import org.epics.ca.Version;

public class DumpVersion
{

   public static void main( String[] args )
   {
      System.out.println ("Java CA v" + Version.getVersionString ());
   }

}
