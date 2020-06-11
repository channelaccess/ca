/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import net.jcip.annotations.ThreadSafe;
import org.epics.ca.util.logging.LibraryLogManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

@ThreadSafe
public class ThreadWatcher
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( ThreadWatcher.class );
   private static final Set<String> jvmManagedThreads = new HashSet<>( Arrays.asList( "process reaper", "Finalizer", "Reference Handler", "Signal Dispatcher", "Attach Listener" ) );
   private final Set<Thread> threadsAtStart;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   private ThreadWatcher( Set<Thread> threadsAtStart )
   {
      logger.finest( "Threads at start: '" + threadsAtStart + "'" );
      this.threadsAtStart = threadsAtStart;
   }

/*- Public methods -----------------------------------------------------------*/

   public static ThreadWatcher start()
   {
      return new ThreadWatcher( getStableThreadSnapshot() );
   }

   public void verify()
   {
      final Set<Thread> threadsAtEndTry1 = getStableThreadSnapshot();
      logger.finest( "Threads at end: try 1: '" + threadsAtEndTry1 + "'" );
      
      if ( userThreadsEqual( threadsAtStart, threadsAtEndTry1, jvmManagedThreads ) )
      {
         return;
      }

      logger.info( "Waiting for thread stabilisation..." );
      final int mediumStabilisationDelayInMillis = 500;
      safeSleep( mediumStabilisationDelayInMillis );
      
      final Set<Thread> threadsAtEndTry2 = getStableThreadSnapshot();
      logger.finest( "Threads at end: try 2: '" + threadsAtEndTry2 + "'" );
      if ( userThreadsEqual( threadsAtStart, threadsAtEndTry2, jvmManagedThreads ) )
      {
         return;
      }

      logger.info( "Waiting for thread stabilisation..." );
      final int longStabilisationDelayInMillis = 1000;
      safeSleep( longStabilisationDelayInMillis );

      final Set<Thread> threadsAtEndTry3 = getStableThreadSnapshot();
      logger.finest( "Threads at end: try 3: '" + threadsAtEndTry2 + "'" );
      if ( userThreadsEqual( threadsAtStart, threadsAtEndTry3, jvmManagedThreads ) )
      {
         return;
      }

      logger.warning( "-- THREAD CHANGES ------------------------------" );
      logger.info( "-- BEFORE -----------------------------------------" );
      showThreads( threadsAtStart );
      logger.info( "-- NOW --------------------------------------------" );
      showThreads( threadsAtEndTry3 );
      throw new RuntimeException( "Threads have changed !" );
   }

   
/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/

   private static boolean userThreadsEqual( Set<Thread> before, Set<Thread> after, Set<String> jvmThreadNames )
   {
      final Set<Thread> filteredBefore = before.stream().filter( x -> ! jvmThreadNames.contains( x.getName().trim() ) ).collect(Collectors.toSet() );
      final Set<Thread> filteredAfter = after.stream().filter( x -> ! jvmThreadNames.contains( x.getName().trim() ) ).collect(Collectors.toSet() );
      return filteredBefore.equals( filteredAfter );
   }

   private static Set<Thread> getStableThreadSnapshot()
   {
      final int shortStabilisationDelayInMillis = 10;
      
      Set<Thread> threadsEarlier = getThreadSnapshot();
      safeSleep( shortStabilisationDelayInMillis );
      Set<Thread> threadsNow = getThreadSnapshot();
      while ( ! threadsNow.equals( threadsEarlier ) )
      {
         threadsEarlier = threadsNow;  
         threadsNow = getThreadSnapshot();
         safeSleep( shortStabilisationDelayInMillis );
      }
      return threadsNow;
   }

   private static Set<Thread> getThreadSnapshot()
   {
      return Thread.getAllStackTraces().keySet();
   }

   private static void safeSleep( int delayInMillis )
   {
      try
      {
         Thread.sleep( delayInMillis );
      }
      catch ( InterruptedException e )
      {
         final String message = "Interrupted whilst waiting for threads to stabilise";
         logger.warning( message );
         throw new RuntimeException( message );
      }
   }   
   
   private static void showThreads( Set<Thread> threadSet) 
   {
      threadSet.forEach( x -> logger.info( x.getName() ) );
   }   
   
/*- Nested Classes -----------------------------------------------------------*/

}
