package org.epics.ca.data;

public class Control<T, ST> extends Graphic<T, ST>
{

   protected ST upperControl;
   protected ST lowerControl;

   public ST getUpperControl()
   {
      return upperControl;
   }

   public void setUpperControl( ST upperControl )
   {
      this.upperControl = upperControl;
   }

   public ST getLowerControl()
   {
      return lowerControl;
   }

   public void setLowerControl( ST lowerControl )
   {
      this.lowerControl = lowerControl;
   }

}