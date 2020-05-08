/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.LoggingListener;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;
import java.util.logging.Level;

import static org.junit.platform.engine.discovery.ClassNameFilter.includeClassNamePatterns;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

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

   private final SummaryGeneratingListener summaryGeneratingListener = new SummaryGeneratingListener();
   private final TestExecutionListener loggingListener = LoggingListener.forJavaUtilLogging( Level.INFO );


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
      final TargetPlatformTestRunner runner = new TargetPlatformTestRunner();

      if ( args.length > 2 )
      {
         System.out.println( "\nUsage: java -cp ca-x.y.z-all-with-tests.jar org.epics.ca.TestRunner [packageSelector] [classFilter]\n" );
         System.out.println( "Concrete Examples:" );
         System.out.println( "[1] Run all tests in the library" );
         System.out.println( "java -cp ca-1.2.3-all-with-tests.jar org.epics.ca.TargetPlatformTestRunner\n\n" );
         System.out.println( "[2] Run all tests in the package named 'repeater'." );
         System.out.println( "java -cp ca-1.2.3-all-with-tests.jar org.epics.ca.TargetPlatformTestRunner org.epics.ca.impl.repeater\n\n" );
         System.out.println( "[3] Run all tests in the package named 'repeater' whose class names match 'SocketUtilitiesTest'." );
         System.out.println( "java -cp ca-1.2.3-all-with-tests.jar org.epics.ca.TargetPlatformTestRunner org.epics.ca.impl.repeater SocketUtilitiesTest\n\n" );

         System.exit( 0 );
      }

      final String packageSelector = args.length >= 1 ? args[ 0 ] : "org.epics.ca";
      final String classNameFilterPatterns = args.length >= 2 ? args[ 1 ] : ".*Test";

      runner.runAll( packageSelector, classNameFilterPatterns );

      final TestExecutionSummary summary = runner.summaryGeneratingListener.getSummary();
      summary.printTo( new PrintWriter( System.out ) );
   }


/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/

   /**
    * Runs the tests specified by the supplied arguments.
    *
    * @param packageSelector selects the top level package to start searching
    *    for tests. For example: "org.epics.ca.impl.repeater".
    * @param classNameFilterPatterns selects the names of classes that should
    *    be tested. For example: ".*Test"
    */
   private void runAll( String packageSelector, String classNameFilterPatterns )
   {
      final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
         .selectors( selectPackage( packageSelector ) )
         .filters( includeClassNamePatterns( classNameFilterPatterns ) )
         .build();

      final Launcher launcher = LauncherFactory.create();
      final TestPlan testPlan = launcher.discover( request );

      for ( TestIdentifier root : testPlan.getRoots())
      {
         System.out.println( "The following test classes have been discovered: " );
         for ( TestIdentifier test : testPlan.getChildren( root  ))
         {
            System.out.println( "- " + test.getDisplayName() );
         }
      }

      launcher.registerTestExecutionListeners( summaryGeneratingListener );
      launcher.registerTestExecutionListeners( loggingListener );

      System.out.println( "RUNNING TESTS..." );
      launcher.execute( request );
   }


/*- Nested Classes -----------------------------------------------------------*/

}
