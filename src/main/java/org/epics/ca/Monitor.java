package org.epics.ca;

public interface Monitor<T> extends AutoCloseable
{
   int VALUE_MASK = 0x01;
   int LOG_MASK = 0x02;
   int ALARM_MASK = 0x04;
   int PROPERTY_MASK = 0x08;

   static int getMask( boolean valueChangeEvent, boolean logEvent, boolean alarmEvent, boolean propertyChangeEvent )
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
