package org.epics.ca;

public class ChannelDescriptor<T>
{

   private String name;
   protected Class<T> type;
   protected Boolean monitored = false;

   public ChannelDescriptor()
   {
   }

   public ChannelDescriptor( String name, Class<T> type )
   {
      this.name = name;
      this.type = type;
   }

   public ChannelDescriptor( String name, Class<T> type, boolean monitored )
   {
      this.name = name;
      this.type = type;
      this.monitored = monitored;
   }

   public Class<T> getType()
   {
      return type;
   }

   public void setType( Class<T> type )
   {
      this.type = type;
   }

   public Boolean getMonitored()
   {
      return monitored;
   }

   public void setMonitored( Boolean monitored )
   {
      this.monitored = monitored;
   }

   public String getName()
   {
      return name;
   }

   public void setName( String name )
   {
      this.name = name;
   }

}