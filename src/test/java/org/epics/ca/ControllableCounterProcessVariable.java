/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import com.cosylab.epics.caj.cas.handlers.AbstractCASResponseHandler;
import com.cosylab.epics.caj.cas.util.NumericProcessVariable;
import gov.aps.jca.CAException;
import gov.aps.jca.CAStatus;
import gov.aps.jca.Monitor;
import gov.aps.jca.cas.ProcessVariableEventCallback;
import gov.aps.jca.cas.ProcessVariableReadCallback;
import gov.aps.jca.cas.ProcessVariableWriteCallback;
import gov.aps.jca.dbr.Severity;
import gov.aps.jca.dbr.Status;
import gov.aps.jca.dbr.*;
import org.epics.ca.util.logging.LibraryLogManager;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/*
 * A version of the CosyLab Controllable Counter whose thread can be shutdown.
 * The original copyright notice follows.
 * 
 * Copyright (c) 2004 by Cosylab
 *
 * The full license specifying the redistribution, modification, usage and other
 * rights and obligations is included with the distribution of this project in
 * the file "LICENSE-CAJ". If the license is not included visit Cosylab web site,
 * <http://www.cosylab.com>.
 *
 * THIS SOFTWARE IS PROVIDED AS-IS WITHOUT WARRANTY OF ANY KIND, NOT EVEN THE
 * IMPLIED WARRANTY OF MERCHANTABILITY. THE AUTHOR OF THIS SOFTWARE, ASSUMES
 * _NO_ RESPONSIBILITY FOR ANY CONSEQUENCE RESULTING FROM THE USE, MODIFICATION,
 * OR REDISTRIBUTION OF THIS SOFTWARE.
 */

/**
 * Example implementation of process variable - counter.
 * Counter starts counting at <code>startValue</code> incrementing by <code>incrementValue</code> every <code>periodInMS</code> milliseconds.
 * When counter value riches <code>envValue</code> counter is reset to <code>startValue</code>.
 * Implementation also triggers alarms (seting status and severity) regarding to set warning and alarm limits.
 * @author msekoranja
 */
public class ControllableCounterProcessVariable extends NumericProcessVariable implements Runnable 
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( ControllableCounterProcessVariable.class );
   
   private final AtomicBoolean shutdownRequest;

   /**
    * Counter start value.
    */
   private final int startValue;

   /**
    * Counter end (stop) value.
    */
   private final int endValue;

   /**
    * Increment value (counter stepping).
    */
   private final int incrementValue;

   /**
    * Period between value increments.
    */
   private final int periodInMS;

   /**
    * Lower warning value.
    */
   private final Number lowerWarningValue;

   /**
    * Upper warning value.
    */
   private final Number upperWarningValue;

   /**
    * Lower alarm value.
    */
   private final Number lowerAlarmValue;

   /**
    * Upper alarm value.
    */
   private final Number upperAlarmValue;

   /**
    * Lower display value (= start value).
    */
   protected Number lowerDisplayValue;

   /**
    * Upper display value (= end value).
    */
   private final Number upperDisplayValue;

   /**
    * Lower control value (= start value).
    */
   private final Number lowerControlValue;

   /**
    * Upper control value (= end value).
    */
   private final Number upperControlValue;

   /**
    * Counter value.
    */
   private int value;

   /**
    * Timestamp of last value change.
    */
   private TimeStamp timestamp;

   /**
    * Value status.
    */
   private Status status;

   /**
    * Value status severity.
    */
   private Severity severity;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
   
   /**
    * Construct a controllable counter PV instance.
    * 
    * @param name PV name.
    * @param eventCallback	event callback, where to report value change events.
    * @param startValue counter start value, where to start counting.
    * @param endValue	counter end value, where to stop counting.
    * @param incrementValue	counter increment value, count steps.
    * @param periodInMS	period in milliseconds between two increments.
    * @param lowerWarningValue lower warning limit value.
    * @param upperWarningValue upper warning limit value.
    * @param lowerAlarmValue lower alarm limit value.
    * @param upperAlarmValue upper alarm limit value.
    */
   private ControllableCounterProcessVariable( String name,
                                               ProcessVariableEventCallback eventCallback,
                                               int startValue, int endValue, int incrementValue, int periodInMS,
                                               int lowerWarningValue, int upperWarningValue,
                                               int lowerAlarmValue, int upperAlarmValue) 
   {
      super( name, eventCallback);

      this.startValue = startValue;
      this.endValue = endValue;
      this.incrementValue = incrementValue;
      this.periodInMS = periodInMS;

      this.lowerWarningValue = lowerWarningValue;
      this.upperWarningValue = upperWarningValue;

      this.lowerAlarmValue = lowerAlarmValue;
      this.upperAlarmValue = upperAlarmValue;

      this.lowerControlValue = startValue;
      this.upperControlValue = endValue;

      this.lowerDisplayValue = lowerControlValue;
      this.upperDisplayValue = upperControlValue;

      this.shutdownRequest = new AtomicBoolean( false );
   }
   
/*- Public methods -----------------------------------------------------------*/

   /**
    * @param name PV name.
    * @param eventCallback	event callback, where to report value change events.
    * @param startValue counter start value, where to start counting.
    * @param endValue	counter end value, where to stop counting.
    * @param incrementValue	counter increment value, count steps.
    * @param periodInMS	period in milliseconds between two increments.
    * @param lowerWarningValue lower warning limit value.
    * @param upperWarningValue upper warning limit value.
    * @param lowerAlarmValue lower alarm limit value.
    * @param upperAlarmValue upper alarm limit value.
    *                        
    * @return the instance.
    */
   public static ControllableCounterProcessVariable start( String name,
                                                           ProcessVariableEventCallback eventCallback,
                                                           int startValue, int endValue, int incrementValue, int periodInMS,
                                                           int lowerWarningValue, int upperWarningValue,
                                                           int lowerAlarmValue, int upperAlarmValue)
   {
      final ControllableCounterProcessVariable counter = new ControllableCounterProcessVariable( name, 
                                                                                                 eventCallback, 
                                                                                                 startValue, endValue, incrementValue, 
                                                                                                 periodInMS, 
                                                                                                 lowerWarningValue, upperWarningValue, 
                                                                                                 lowerAlarmValue, upperAlarmValue );
      counter.initialize();
      return counter;
   }   
   
   public void shutdown()
   {
      shutdownRequest.set( true );
   }   
   
   /**
    * Return <code>DBRType.INT</code> type as native type.
    * @see gov.aps.jca.cas.ProcessVariable#getType()
    */
   public DBRType getType() {
      return DBRType.INT;
   }

   /**
    * @see NumericProcessVariable#getLowerAlarmLimit()
    */
   public Number getLowerAlarmLimit() {
      return lowerAlarmValue;
   }

   /**
    * @see NumericProcessVariable#getLowerCtrlLimit()
    */
   public Number getLowerCtrlLimit() {
      return lowerControlValue;
   }

   /**
    * @see NumericProcessVariable#getLowerDispLimit()
    */
   public Number getLowerDispLimit() {
      return lowerDisplayValue;
   }

   /**
    * @see NumericProcessVariable#getLowerWarningLimit()
    */
   public Number getLowerWarningLimit() {
      return lowerWarningValue;
   }

   /**
    * @see NumericProcessVariable#getUnits()
    */
   public String getUnits() {
      return GR.EMPTYUNIT;
   }

   /**
    * @see NumericProcessVariable#getUpperAlarmLimit()
    */
   public Number getUpperAlarmLimit() {
      return upperAlarmValue;
   }

   /**
    * @see NumericProcessVariable#getUpperCtrlLimit()
    */
   public Number getUpperCtrlLimit() {
      return upperControlValue;
   }

   /**
    * @see NumericProcessVariable#getUpperDispLimit()
    */
   public Number getUpperDispLimit() {
      return upperDisplayValue;
   }

   /**
    * @see NumericProcessVariable#getUpperWarningLimit()
    */
   public Number getUpperWarningLimit() {
      return upperWarningValue;
   }

   /**
    * Read value.
    * DBR is already filled-in by <code>com.cosylab.epics.caj.cas.util.NumericProcessVariable#read()</code> method.
    * @see NumericProcessVariable#readValue(DBR, ProcessVariableReadCallback)
    */
   public synchronized CAStatus readValue( DBR value, ProcessVariableReadCallback asyncReadCallback )
   {
      // it is always at least DBR_TIME_Int DBR
      DBR_TIME_Int timeDBR = (DBR_TIME_Int)value;

      // set status and time
      fillInStatusAndTime(timeDBR);

      // set scalar value
      timeDBR.getIntValue()[0] = this.value;

      // return read completion status
      return CAStatus.NORMAL;
   }
   
   /**
    * Write value.
    * @see NumericProcessVariable#writeValue(DBR, ProcessVariableWriteCallback)
    */
   public synchronized CAStatus writeValue( DBR value, ProcessVariableWriteCallback asyncWriteCallback )
   {
      // it is always at least DBR_Int DBR
      DBR_Int intDBR = (DBR_Int)value;

      // check value
      int val = intDBR.getIntValue()[0];
      if (val < startValue || val > endValue)
         return CAStatus.PUTFAIL;

      // set value, status and alarm
      this.value = val;
      timestamp = new TimeStamp();
      checkForAlarms();

      // post event if there is an interest
      if (interest)
      {
         // set event mask
         int mask = Monitor.VALUE | Monitor.LOG;
         if (status != Status.NO_ALARM)
            mask |= Monitor.ALARM;

         // create and fill-in DBR
         DBR monitorDBR = AbstractCASResponseHandler.createDBRforReading(this);
         ((DBR_Int)monitorDBR).getIntValue()[0] = this.value;
         fillInDBR(monitorDBR);
         fillInStatusAndTime((TIME)monitorDBR);

         // port event
         eventCallback.postEvent(mask, monitorDBR);
      }
      return CAStatus.NORMAL;
   }
   
/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/
   
   /**
    * Initialize PV.
    * Sets initial counter state and spawns a counter thread.
    */
   private void initialize()
   {
      value = startValue;
      timestamp = new TimeStamp();
      checkForAlarms();

      final Thread thread = new Thread(this, getName() + " counter");
      thread.setDaemon( true );
      thread.start();
   }

   /**
    * Checks for alarms (sets <code>status</code> and <code>severity</code>).
    */
   private void checkForAlarms() {

      severity = Severity.MINOR_ALARM;

      if (value >= upperAlarmValue.intValue())
         status = Status.HIHI_ALARM;
      else if (value >= upperWarningValue.intValue())
         status = Status.HIGH_ALARM;
      else if (value <= lowerAlarmValue.intValue())
         status = Status.LOLO_ALARM;
      else if (value <= lowerWarningValue.intValue())
         status = Status.LOW_ALARM;
      else
      {
         status = Status.NO_ALARM;
         severity = Severity.NO_ALARM;
      }
   }
   
   /**
    * Fill-in status and time to DBR.
    * @param timeDBR DBR to fill-in.
    */
   private void fillInStatusAndTime(TIME timeDBR)
   {
      // set status and severity
      timeDBR.setStatus(status);
      timeDBR.setSeverity(severity);

      // set timestamp
      timeDBR.setTimeStamp(timestamp);
   }
   
   /**
    * Counting (external trigger) is done in this separate thread.
    * @see Runnable#run()
    */
   @Override
   public void run() {

      // initialize DBR for writting
      final DBR_Int valueHolder = new DBR_Int(1);
      final int[] valueArray = valueHolder.getIntValue();

      while ( !shutdownRequest.get() )
      {
         try
         {
            //noinspection BusyWait
            Thread.sleep( periodInMS );
         }
         catch (InterruptedException e)
         {
            break;
         }

         synchronized( this )
         {
            // calculate new value
            int newValue = value + incrementValue;
            if (newValue > endValue)
            {
               newValue = startValue;
            }   

            // set value to DBR
            valueArray[ 0 ] = newValue;

            // write to PV
            try 
            {
               write( valueHolder, null);
            } 
            catch ( CAException ex ) 
            {
               logger.log( Level.SEVERE, "", ex );
            }

         }
      }
   }

   
/*- Nested Classes -----------------------------------------------------------*/
   
}
