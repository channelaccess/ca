package org.epics.ca.util.sync;

import java.util.HashMap;
import java.util.Map;

/**
 * Named lock implementation (I named it "Named-lock pattern").
 * <code>
 * // try to acquire named lock
 * boolean lockAcquired = namedLocker.acquireSynchronizationObject(namedObject, getLockTimeout());
 * if (lockAcquired)
 * {
 * try
 * {
 * // ... so sth here
 * }
 * finally
 * {
 * namedLocker.releaseSynchronizationObject(name);
 * }
 * }
 * else
 * {
 * // .. failed to obtain synchronization lock for component 'namedObject', possible deadlock
 * }
 * </code>
 */
public final class NamedLockPattern
{

   /**
    * Map of (named) locks.
    */
   private final Map<Object, ReferenceCountingLock> namedLocks = new HashMap<> ();

   /**
    * Acquire synchronization lock for named object.
    *
    * @param   name   name of the object whose lock to acquire.
    * @param   msec   the number of milliseconds to wait.
    * An argument less than or equal to zero means not to wait at all.
    * @return   <code>true</code> if acquired, <code>false</code> otherwise.
    */
   public boolean acquireSynchronizationObject( Object name, long msec )
   {
      ReferenceCountingLock lock;

      synchronized ( namedLocks )
      {
         // get synchronization object
         lock = namedLocks.get (name);

         // none is found, create and return new one
         // increment references
         if ( lock == null )
         {
            lock = new ReferenceCountingLock ();
            namedLocks.put (name, lock);
         }
         else
            lock.increment ();
      }

      boolean success = lock.acquire (msec);

      if ( !success )
         releaseSynchronizationObject (name, false);

      return success;
   }

   /**
    * Release synchronization lock for named object.
    *
    * @param   name   name of the object whose lock to release.
    */
   public void releaseSynchronizationObject( Object name )
   {
      releaseSynchronizationObject (name, true);
   }

   /**
    * Release synchronization lock for named object.
    *
    * @param   name   name of the object whose lock to release.
    * @param   release   set to <code>false</code> if there is no need to call release
    * on synchronization lock.
    */
   public void releaseSynchronizationObject( Object name, boolean release )
   {
      synchronized ( namedLocks )
      {
         // get synchronization object
         ReferenceCountingLock lock = namedLocks.get (name);

         // release lock
         if ( lock != null )
         {
            // if there only one current lock exists
            // remove it from the map
            if ( lock.decrement () <= 0 )
               namedLocks.remove (name);

            // release the lock
            if ( release )
               lock.release ();
         }
      }
   }

}
