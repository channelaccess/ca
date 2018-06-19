package org.epics.ca;

public enum Severity
{
   WARNING (0x00000000),
   SUCCESS (0x00000001),
   ERROR (0x00000002),
   INFO (0x00000003),
   SEVERE (0x00000004),
   FATAL (0x00000006);

   private final int value;

   private Severity( int value )
   {
      this.value = value;
   }

   public int getValue()
   {
      return value;
   }
}
