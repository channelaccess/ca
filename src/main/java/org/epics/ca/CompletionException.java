package org.epics.ca;

public class CompletionException extends RuntimeException
{

   private static final long serialVersionUID = 1729705755934441915L;

   private final Status status;

   public CompletionException( Status status, String message )
   {
      super (message);
      this.status = status;
   }

   public Status getStatus()
   {
      return status;
   }

}
