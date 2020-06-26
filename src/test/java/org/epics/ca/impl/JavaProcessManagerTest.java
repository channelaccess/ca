/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.impl.repeater.UdpSocketTester;
import org.junit.jupiter.api.Test;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class JavaProcessManagerTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/
/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @Test
   void testIsAlive_whenInvokedBeforeProcessStart_doesNotThrowException()
   {
      final Properties properties = new Properties();
      final String[] programArgs = new String[] {};
      JavaProcessManager processManager = new JavaProcessManager( UdpSocketTester.class, properties, programArgs );
      assertDoesNotThrow( processManager::isAlive );
   }

/*- Nested Classes -----------------------------------------------------------*/

}

