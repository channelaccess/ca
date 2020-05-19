package org.epics.ca;

import java.util.Optional;

/**
 * Returns the CA Library Version.
 */
public final class LibraryVersion
{

   /**
    * Returns a best-efforts string representation of the CA library that is
    * running inside the current JVM by extracting information from the
    * manifest file, if any, associated with the top level CA package.
    *
    * @return the version string or &lt;unknown&gt; if unavailable.
    */
   public static String getAsString()
   {
      final String unknownVersion = "<unknown>";
      final Optional<Package> optPackage = Optional.ofNullable( LibraryVersion.class.getPackage() );

      if ( optPackage.isPresent() )
      {
         final Optional<String> optPackageImplementationVersion = Optional.ofNullable( optPackage.get().getImplementationVersion() );
         final Optional<String> optPackageSpecificationVersion = Optional.ofNullable( optPackage.get().getSpecificationVersion() );

         if ( optPackageImplementationVersion.isPresent() )
         {
            return optPackageImplementationVersion.get();
         }

         if ( optPackageSpecificationVersion.isPresent() )
         {
            return optPackageSpecificationVersion.get();
         }
      }

      return unknownVersion;
   }

}
