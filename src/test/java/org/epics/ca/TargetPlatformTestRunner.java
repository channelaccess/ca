/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.impl.repeater.NetworkUtilities;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.LoggingListener;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.platform.engine.discovery.DiscoverySelectors.*;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Provides a mechanism for running tests from the command-line directly on the
 * target platform.
 */
public class TargetPlatformTestRunner
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( TargetPlatformTestRunner.class );

   private final SummaryGeneratingListener summaryGeneratingListener;
   private final TestExecutionListener loggingListener;

/*- Main ---------------------------------------------------------------------*/

   /**
    * Runs defined tests in the specified packages and classes.
    *
    * Usage: java -cp ca-x.y.z-all-with-tests.jar org.epics.ca.PlatformTestRunner [packageSelector] [classFilter]
    *
    * @param args arguments that specify the tests to run.
    */
   public static void main( String[] args)
   {
      if( ! NetworkUtilities.verifyTargetPlatformNetworkStackIsChannelAccessCompatible() )
      {
         return;
      }

      final TargetPlatformTestRunner runner = new TargetPlatformTestRunner();

      if ( ( args.length != 0 ) && ( args.length != 2 ) )
      {
         printUsage();
         return;
      }

      if ( args.length == 0 )
      {
         runner.runTests( "-ALL", "" );
      }
      else
      {
         runner.runTests( args[ 0 ], args[ 1 ] );
      }

      final TestExecutionSummary summary = runner.getSummary();
      summary.printTo( new PrintWriter( System.out ) );
   }


/*- Constructor --------------------------------------------------------------*/

   public TargetPlatformTestRunner()
   {
      this.summaryGeneratingListener = new SummaryGeneratingListener();
      this.loggingListener = LoggingListener.forJavaUtilLogging( Level.INFO );
   }

/*- Public methods -----------------------------------------------------------*/

   private TestExecutionSummary getSummary()
   {
      return summaryGeneratingListener.getSummary();
   }

/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/

   private static void printUsage()
   {
      System.out.println( "\nUsage: java -cp ca-x.y.z-all-with-tests.jar org.epics.ca.TargetPlatformTestRunner [{-package|-class|-method} testSelector]\n" );
      System.out.println( "Concrete Examples:" );
      System.out.println( "[1] Run all the tests in the library." );
      System.out.println( "java -cp ca-1.2.3-all-with-tests.jar org.epics.ca.TargetPlatformTestRunner\n" );
      System.out.println( "\n" );
      System.out.println( "[2] Run all the tests in the package named 'repeater'." );
      System.out.println( "java -cp ca-1.2.3-all-with-tests.jar org.epics.ca.TargetPlatformTestRunner -package org.epics.ca.impl.repeater\n" );
      System.out.println( "\n" );
      System.out.println( "[3] Run all the tests in the class named 'org.epics.ca.impl.repeater.SocketUtilitiesTest'." );
      System.out.println( "java -cp ca-1.2.3-all-with-tests.jar -class org.epics.ca.impl.repeater.SocketUtilitiesTest\n" );
      System.out.println( "\n" );
      System.out.println( "[4] Run the test with the method named 'org.epics.ca.impl.repeater.SocketUtilitiesTest#integrationTestDataTransfer'." );
      System.out.println( "java -cp ca-1.2.3-all-with-tests.jar org.epics.ca.TargetPlatformTestRunner -method org.epics.ca.impl.repeater.SocketUtilitiesTest#integrationTestDataTransfer\n" );
      System.out.println( "\n" );
      System.exit( 0 );
   }

   /**
    * Runs the tests specified by the supplied arguments.
    *
    * The testType selects whether all the tests in the test suite should be run, or whether
    * the tests should be specific to some particular package, or class or method.
    *
    * @param testType selects the type of test to be run. Should be one of [ -all | -package | -class | -method ].
    * @param testSpecifier specifies the test to be run. Should be a fully-qualified package name or class name
    *    or method name.
    */
   private void runTests( String testType, String testSpecifier )
   {
      final LauncherDiscoveryRequest request;
      switch ( testType.toUpperCase() )
      {
         case "-ALL":
            request = LauncherDiscoveryRequestBuilder.request().selectors( selectPackage( "org.epics.ca" ) ).build();
            break;

         case "-PACKAGE":
            request = LauncherDiscoveryRequestBuilder.request().selectors( selectPackage( testSpecifier ) ).build();
            break;

         case "-CLASS":
            request = LauncherDiscoveryRequestBuilder.request().selectors( selectClass( testSpecifier ) ).build();
            break;

         case "-METHOD":
            request = LauncherDiscoveryRequestBuilder.request().selectors( selectMethod( testSpecifier ) ).build();
            break;

         default:
            printUsage();
            return;
      }

      final Launcher launcher = LauncherFactory.create();
      final TestPlan testPlan = launcher.discover( request );

      launcher.registerTestExecutionListeners( summaryGeneratingListener );
      launcher.registerTestExecutionListeners( loggingListener );

      try
      {
         logger.info("RUNNING TEST(S)...");
         launcher.execute( testPlan );
         logger.info( "TEST(S) COMPLETED." );
      }
      catch ( RuntimeException ex )
      {
         logger.info( "TEST(S) FAILED WITH EXCEPTION." );
      }

   }


/*- Nested Classes -----------------------------------------------------------*/

}
