/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl;


/*- Imported packages --------------------------------------------------------*/

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import java.util.logging.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.Matchers.notNullValue;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class LibraryConfigurationTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/
/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @Test
   void testDefaults()
   {
      final LibraryConfiguration instance = LibraryConfiguration.getInstance();
      assertThat( instance, is( notNullValue() ) );
      assertThat( ! instance.isRepeaterEnabled(), is( LibraryConfiguration.CA_REPEATER_DISABLE_DEFAULT ) );
      assertThat( instance.isRepeaterOutputCaptureEnabled(), is( LibraryConfiguration.CA_REPEATER_OUTPUT_CAPTURE_DEFAULT ) );
      assertThat( instance.getLibraryLogLevel(), is( LibraryConfiguration.CA_LIBRARY_LOG_LEVEL_LOG_LEVEL_DEFAULT ) );
      assertThat( instance.getRepeaterLogLevel(), is( LibraryConfiguration.CA_REPEATER_LOG_LEVEL_DEFAULT ) );
      assertThat( instance.getMonitorNotifierImplementation(), is( LibraryConfiguration.CA_MONITOR_NOTIFIER_IMPL_DEFAULT ) );
   }

   @Test
   void testSystemPropertyOverride()
   {
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE.name(), "yes" );
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_OUTPUT_CAPTURE.name(), "no" );
      System.setProperty( LibraryConfiguration.PropertyNames.CA_LIBRARY_LOG_LEVEL.name(), Level.CONFIG.toString() );
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_LOG_LEVEL.name(), Level.FINER.toString() );
      System.setProperty( LibraryConfiguration.PropertyNames.CA_MONITOR_NOTIFIER_IMPL.name(), "someMonitorNotifierEngineImpl" );

      final LibraryConfiguration instance = LibraryConfiguration.getInstance();

      assertThat( instance, is( notNullValue() ) );
      assertThat( instance.isRepeaterEnabled(), is( false ) );
      assertThat( instance.isRepeaterOutputCaptureEnabled(), is( false )  );
      assertThat( instance.getLibraryLogLevel(), is( Level.CONFIG )  );
      assertThat( instance.getRepeaterLogLevel(), is( Level.FINER )  );
      assertThat( instance.getMonitorNotifierImplementation(), is( "someMonitorNotifierEngineImpl" )  );

      // Restore old environment for future tests.
      System.clearProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE.name() );
      System.clearProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_OUTPUT_CAPTURE.name() );
      System.clearProperty( LibraryConfiguration.PropertyNames.CA_LIBRARY_LOG_LEVEL.name() );
      System.clearProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_LOG_LEVEL.name() );
      System.clearProperty( LibraryConfiguration.PropertyNames.CA_MONITOR_NOTIFIER_IMPL.name() );
   }

   @Test
   void testEnvironmentalVariablesOverrides() throws Exception
   {
      final Map<String,String> envMap = new HashMap<>();
      envMap.put( LibraryConfiguration.PropertyNames.CA_LIBRARY_LOG_LEVEL.name(),Level.ALL.toString() );
      envMap.put( LibraryConfiguration.PropertyNames.CA_MONITOR_NOTIFIER_IMPL.name(), "someOtherMonitorNotifierEngineImpl" );
      setEnv( envMap );

      System.setProperty( LibraryConfiguration.PropertyNames.CA_LIBRARY_LOG_LEVEL.name(), Level.OFF.toString() );

      final LibraryConfiguration instance = LibraryConfiguration.getInstance();
      assertThat( instance, is( notNullValue() ) );
      assertThat( instance.getLibraryLogLevel(), is( Level.OFF ) );
      assertThat( instance.getMonitorNotifierImplementation(), is( "someOtherMonitorNotifierEngineImpl" )  );

      // Restore old environment for future tests.
      envMap.clear();
      setEnv( envMap );
      System.clearProperty( LibraryConfiguration.PropertyNames.CA_LIBRARY_LOG_LEVEL.name() );
   }

/*- Private methods ----------------------------------------------------------*/
   /**
    * This method provides a hack for setting the OS environment.
    * See https://stackoverflow.com/questions/318239/how-do-i-set-environment-variables-from-java
    */
   @SuppressWarnings( { "unchecked", "rawtypes","JavaReflectionMemberAccess" } )
   private static void setEnv( Map<String, String> newenv ) throws Exception
   {
      try
      {
         Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
         Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
         theEnvironmentField.setAccessible(true);
         Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
         env.putAll(newenv);
         Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment" );
         theCaseInsensitiveEnvironmentField.setAccessible(true);
         Map<String, String> cienv = (Map<String, String>)     theCaseInsensitiveEnvironmentField.get(null);
         cienv.putAll(newenv);
      }
      catch (NoSuchFieldException e)
      {
         Class[] classes = Collections.class.getDeclaredClasses();
         Map<String, String> env = System.getenv();
         for(Class cl : classes) {
            if("java.util.Collections$UnmodifiableMap".equals(cl.getName()))
            {
               Field field = cl.getDeclaredField("m");
               field.setAccessible(true);
               Object obj = field.get(env);
               Map<String, String> map = (Map<String, String>) obj;
               map.clear();
               map.putAll(newenv);
            }
         }
      }
   }

/*- Nested Classes -----------------------------------------------------------*/

}
