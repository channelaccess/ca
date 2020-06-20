/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl;


/*- Imported packages --------------------------------------------------------*/

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class ProtocolConfigurationTest
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
      final ProtocolConfiguration instance = new ProtocolConfiguration();
      assertThat( instance, is( notNullValue() ) );
      assertThat( instance.getAddressList(), is( ProtocolConfiguration.EPICS_CA_ADDR_LIST_DEFAULT ) );
      assertThat( instance.getAutoAddressList(), is( ProtocolConfiguration.EPICS_CA_AUTO_ADDR_LIST_DEFAULT ) );
      assertThat( instance.getConnectionTimeout(), is( ProtocolConfiguration.EPICS_CA_CONN_TMO_DEFAULT ) );
      assertThat( instance.getMaxArrayBytes(), is( ProtocolConfiguration.EPICS_CA_MAX_ARRAY_BYTES_DEFAULT ) );
      assertThat( instance.getRepeaterPort(), is( ProtocolConfiguration.EPICS_CA_REPEATER_PORT_DEFAULT ) );
      assertThat( instance.getServerPort(), is( ProtocolConfiguration.EPICS_CA_SERVER_PORT_DEFAULT ) );
   }

   @Test
   void testSystemPropertyOverrides()
   {
      final Properties propertyOverrides = new Properties();
      propertyOverrides.setProperty( ProtocolConfiguration.PropertyNames.EPICS_CA_ADDR_LIST.name(), "abcd" );
      propertyOverrides.setProperty( ProtocolConfiguration.PropertyNames.EPICS_CA_AUTO_ADDR_LIST.name(), "false" );
      propertyOverrides.setProperty( ProtocolConfiguration.PropertyNames.EPICS_CA_CONN_TMO.name(), "21.3" );
      propertyOverrides.setProperty( ProtocolConfiguration.PropertyNames.EPICS_CA_MAX_ARRAY_BYTES.name(), "123456" );
      propertyOverrides.setProperty( ProtocolConfiguration.PropertyNames.EPICS_CA_REPEATER_PORT.name(), "5421" );
      propertyOverrides.setProperty( ProtocolConfiguration.PropertyNames.EPICS_CA_SERVER_PORT.name(), "9977" );

      final ProtocolConfiguration instance = new ProtocolConfiguration( propertyOverrides );
      assertThat( instance, is( notNullValue() ) );
      assertThat( instance.getAddressList(), is( "abcd" ) );
      assertThat( instance.getAutoAddressList(), is( false )  );
      assertThat( (double) instance.getConnectionTimeout(), closeTo( 21.3, 0.00001 )  );
      assertThat( instance.getMaxArrayBytes(), is(123456 )  );
      assertThat( instance.getRepeaterPort(), is( 5421 )  );
      assertThat( instance.getServerPort(), is( 9977 )  );
   }

   @EnabledOnOs( {OS.MAC, OS.LINUX} )
   @Test
   void testEnvironmentalVariablesOverrides() throws Exception
   {
      final Map<String,String> envMap = new HashMap<>();
      envMap.put( ProtocolConfiguration.PropertyNames.EPICS_CA_ADDR_LIST.name(), "xyz" );
      envMap.put( ProtocolConfiguration.PropertyNames.EPICS_CA_CONN_TMO.name(), "19.2" );
      setEnv( envMap );

      final Properties propertyOverrides = new Properties();
      propertyOverrides.setProperty( ProtocolConfiguration.PropertyNames.EPICS_CA_CONN_TMO.name(), "21.3" );

      final ProtocolConfiguration instance = new ProtocolConfiguration( propertyOverrides );
      assertThat( instance, is( notNullValue() ) );
      assertThat( instance.getAddressList(), is( "xyz" ) );
      assertThat( (double) instance.getConnectionTimeout(), closeTo( 21.3, 0.00001 )  );

      // Restore old environment for future tests.
      envMap.clear();
      setEnv( envMap );
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
