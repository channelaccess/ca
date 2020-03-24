package org.epics.ca.data;

public class Timestamped<T> extends Alarm<T>
{

   public static final long EPOCH_SECONDS_PAST_1970 = 7305 * 86400L;

   /**
    * past 1.1.2970
    */
   protected long seconds;
   protected int nanos;

   public long getSeconds()
   {
      return seconds;
   }

   public void setSeconds( long seconds )
   {
      this.seconds = seconds;
   }

   public int getNanos()
   {
      return nanos;
   }

   public void setNanos( int nanos )
   {
      this.nanos = nanos;
   }

   public long getMillis()
   {
      return seconds * 1000 + nanos / 1000000;
   }
}