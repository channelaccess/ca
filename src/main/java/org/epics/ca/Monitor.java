package org.epics.ca;

import org.epics.ca.util.Holder;

import com.lmax.disruptor.dsl.Disruptor;

public interface Monitor<T> extends AutoCloseable
{

   public static final int VALUE_MASK = 0x01;
   public static final int LOG_MASK = 0x02;
   public static final int ALARM_MASK = 0x04;
   public static final int PROPERTY_MASK = 0x08;

   public static int getMask( boolean valueChangeEvent, boolean logEvent, boolean alarmEvent, boolean propertyChangeEvent )
   {
      int mask = (valueChangeEvent ? VALUE_MASK : 0);
      if ( logEvent )
         mask |= LOG_MASK;
      if ( alarmEvent )
         mask |= ALARM_MASK;
      if ( propertyChangeEvent )
         mask |= PROPERTY_MASK;

      if ( mask == 0 )
         throw new IllegalArgumentException ("mask == 0");

      return mask;
   }

   // suppresses AutoCloseable.close() exception
   @Override
   void close();
}
