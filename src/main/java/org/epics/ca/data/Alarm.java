package org.epics.ca.data;

public class Alarm<T> extends Metadata<T>
{

   protected AlarmStatus alarmStatus = AlarmStatus.UDF_ALARM;
   protected AlarmSeverity alarmSeverity = AlarmSeverity.INVALID_ALARM;

   public AlarmStatus getAlarmStatus()
   {
      return alarmStatus;
   }

   public void setAlarmStatus( AlarmStatus alarmStatus )
   {
      this.alarmStatus = alarmStatus;
   }

   public AlarmSeverity getAlarmSeverity()
   {
      return alarmSeverity;
   }

   public void setAlarmSeverity( AlarmSeverity alarmSeverity )
   {
      this.alarmSeverity = alarmSeverity;
   }
}