package org.epics.ca.impl;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.epics.ca.data.Alarm;
import org.epics.ca.data.AlarmSeverity;
import org.epics.ca.data.AlarmStatus;
import org.epics.ca.data.Control;
import org.epics.ca.data.Graphic;
import org.epics.ca.data.GraphicEnum;
import org.epics.ca.data.GraphicEnumArray;
import org.epics.ca.data.Timestamped;

import com.lmax.disruptor.EventFactory;


public class TypeSupports
{
   /**
    * Threshold at which the method to copy the buffer is changed.
    * If lower, the elements are copied one by one. If higher, the
    * data is copied with ByteBuffer bulk operations.
    * <p>
    * As of JDK 1.7 the optimization at lower count is desirable because there is a
    * fair amount of logic implemented in ByteBuffer bulk operations
    * to determine which methods are available and which one is more
    * efficient.
    */
   private static final int OPTIMIZED_COPY_THRESHOLD = 128;

   /**
    * Gets the TypeSupport for the specified type class.
    *
    * @param typeClass the type class.
    * @return the support class.
    */
   static TypeSupport<?> getTypeSupportForType( Class<?> typeClass )
   {
      return getTypeSupportForMetatypeAndType(Void.class, typeClass );
   }

   /**
    * Gets the TypeSupport for the specified metatype class and type class.
    *
    * @param metaTypeClass the metatype class.
    * @param typeClass the type class.
    * @return the support class or null if not found.
    */
   static TypeSupport<?> getTypeSupportForMetatypeAndType( Class<?> metaTypeClass, Class<?> typeClass )
   {
      // special case(s)
      if ( metaTypeClass == GraphicEnum.class )
      {
         return GraphicEnumTypeSupport.INSTANCE;
      }

      if ( metaTypeClass == GraphicEnumArray.class )
      {
         return GraphicEnumArrayTypeSupport.INSTANCE;
      }

      if ( typeSupportMap.containsKey( metaTypeClass) )
      {
         return typeSupportMap.get( metaTypeClass).get( typeClass );
      }

      return null;
   }

   /**
    * Gets the TypeSupport corresponding for the specified typeCode and elementCount.
    *
    * @param typeCode the type code.
    * @param elementCount the element count.
    * @return the support class or null if not found.
    */
   static TypeSupport<?> getTypeSupport( short typeCode, int elementCount )
   {
      if ( matches( GraphicEnumTypeSupport.INSTANCE, typeCode, elementCount ) )
      {
         return GraphicEnumTypeSupport.INSTANCE;
      }

      if ( matches (GraphicEnumArrayTypeSupport.INSTANCE, typeCode, elementCount) )
      {
         return GraphicEnumArrayTypeSupport.INSTANCE;
      }

      if ( typeSupportMap.containsKey( Void.class ) )
      {
         for ( TypeSupport<?> typeSupport : typeSupportMap.get( Void.class ).values() )
         {
            if ( matches( typeSupport, typeCode, elementCount) )
            {
               return typeSupport;
            }
         }
      }

      return null;
   }

   static boolean isNativeType( Class<?> typeClass )
   {
      return nativeTypeSet.contains( typeClass );
   }


   /**
    * Create (extract) string (zero-terminated) from byte buffer.
    *
    * @param rawBuffer the buffer.
    * @return decoded DBR.
    */
   private static String extractString( byte[] rawBuffer )
   {
      final int rawBufferLen = rawBuffer.length;
      int len = 0;
      while ( len < rawBufferLen && rawBuffer[ len ] != 0 )
      {
         len++;
      }
      return new String (rawBuffer, 0, len);
   }

   private static void readAlarm( ByteBuffer buffer, Alarm<?> data )
   {
      final int status = buffer.getShort () & 0xFFFF;
      final int severity = buffer.getShort () & 0xFFFF;

      data.setAlarmStatus (AlarmStatus.values ()[ status ]);
      data.setAlarmSeverity (AlarmSeverity.values ()[ severity ]);
   }

   private static void readTimestamp( ByteBuffer buffer, Timestamped<?> data )
   {
      // seconds since 0000 Jan 1, 1990
      final long secPastEpoch = buffer.getInt () & 0x00000000FFFFFFFFL;

      // nanoseconds within second
      final int nsec = buffer.getInt ();

      data.setSeconds (secPastEpoch + Timestamped.EPOCH_SECONDS_PAST_1970);
      data.setNanos (nsec);
   }

   private static void readUnits( ByteBuffer buffer, Graphic<?, ?> data )
   {
      final int MAX_UNITS_SIZE = 8;
      final byte[] rawUnits = new byte[ MAX_UNITS_SIZE ];
      buffer.get (rawUnits);

      data.setUnits (extractString (rawUnits));
   }

   private static void readPrecision( ByteBuffer buffer, Graphic<?, ?> data )
   {
      int precision = buffer.getShort () & 0xFFFF;
      data.setPrecision (precision);

      // RISC padding
      buffer.getShort ();
   }

   private static <T> void readGraphicLimits( TypeSupport<T> valueReader, ByteBuffer buffer, Graphic<?, T> data )
   {
      data.setUpperDisplay (valueReader.deserialize (buffer, data.getUpperDisplay (), 1));
      data.setLowerDisplay (valueReader.deserialize (buffer, data.getLowerDisplay (), 1));
      data.setUpperAlarm (valueReader.deserialize (buffer, data.getUpperAlarm (), 1));
      data.setUpperWarning (valueReader.deserialize (buffer, data.getUpperWarning (), 1));
      data.setLowerWarning (valueReader.deserialize (buffer, data.getLowerWarning (), 1));
      data.setLowerAlarm (valueReader.deserialize (buffer, data.getLowerAlarm (), 1));
   }

   private static <T> void readControlLimits( TypeSupport<T> valueReader, ByteBuffer buffer, Control<?, T> data )
   {
      data.setUpperControl (valueReader.deserialize (buffer, data.getUpperControl (), 1));
      data.setLowerControl (valueReader.deserialize (buffer, data.getLowerControl (), 1));
   }

   public interface TypeSupport<T> extends EventFactory<T>
   {
      int getDataType();

      default int getForcedElementCount()
      {
         return 0;
      }

      default void serialize( ByteBuffer buffer, T object, int count )
      {
         throw new UnsupportedOperationException ();
      }

      default int serializeSize( T object, int count )
      {
         throw new UnsupportedOperationException ();
      }

      T deserialize( ByteBuffer buffer, T object, int count );
   }

   /**-----------------------------------------------------------------------------------*
    * MetadataTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static abstract class MetadataTypeSupport<T, VT> implements TypeSupport<T>
   {
      protected final TypeSupport<VT> valueTypeSupport;
      protected final int preValuePadding;

      public MetadataTypeSupport( TypeSupport<VT> valueTypeSupport, int preValuePadding )
      {
         this.valueTypeSupport = valueTypeSupport;
         this.preValuePadding = preValuePadding;
      }

      @Override
      public int getForcedElementCount()
      {
         return valueTypeSupport.getForcedElementCount ();
      }

      public void preValuePad( ByteBuffer buffer )
      {
         buffer.position (buffer.position () + preValuePadding);
      }
   }

   /**-----------------------------------------------------------------------------------*
    * STSTypeSupport
    *-----------------------------------------------------------------------------------*/
   private static class STSTypeSupport<T> extends MetadataTypeSupport<Alarm<T>, T>
   {
      private STSTypeSupport( TypeSupport<T> valueTypeSupport, int preValuePadding )
      {
         super (valueTypeSupport, preValuePadding);
      }

      @Override
      public Alarm<T> newInstance()
      {
         return new Alarm<> ();
      }

      @Override
      public int getDataType()
      {
         return valueTypeSupport.getDataType () + 7;
      }

      @Override
      public Alarm<T> deserialize( ByteBuffer buffer, Alarm<T> object, int count )
      {
         Alarm<T> data = (object == null) ? newInstance () : object;

         readAlarm( buffer, data );

         preValuePad( buffer );
         data.setValue( valueTypeSupport.deserialize (buffer, data.getValue (), count) );

         return data;
      }
   }

   /**-----------------------------------------------------------------------------------*
    * TimeTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static class TimeTypeSupport<T> extends MetadataTypeSupport<Timestamped<T>, T>
   {
      private TimeTypeSupport( TypeSupport<T> valueTypeSupport, int preValuePadding )
      {
         super (valueTypeSupport, preValuePadding);
      }

      @Override
      public Timestamped<T> newInstance()
      {
         return new Timestamped<> ();
      }

      @Override
      public int getDataType()
      {
         return valueTypeSupport.getDataType () + 14;
      }

      @Override
      public Timestamped<T> deserialize( ByteBuffer buffer, Timestamped<T> object, int count )
      {
         final Timestamped<T> data = (object == null) ? newInstance () : object;

         readAlarm( buffer, data );
         readTimestamp( buffer, data );

         preValuePad( buffer );
         data.setValue (valueTypeSupport.deserialize (buffer, data.getValue (), count));

         return data;
      }
   }

   /**-----------------------------------------------------------------------------------*
    * GraphicTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static class GraphicTypeSupport<T, ST> extends MetadataTypeSupport<Graphic<T, ST>, T>
   {
      protected final TypeSupport<ST> scalarValueTypeSupport;

      private GraphicTypeSupport( TypeSupport<T> valueTypeSupport, int preValuePadding,
                                  TypeSupport<ST> scalarValueTypeSupport )
      {
         super (valueTypeSupport, preValuePadding);
         this.scalarValueTypeSupport = scalarValueTypeSupport;
      }

      @Override
      public Graphic<T, ST> newInstance()
      {
         return new Graphic<>();
      }

      @Override
      public int getDataType()
      {
         return valueTypeSupport.getDataType () + 21;
      }

      @Override
      public Graphic<T, ST> deserialize( ByteBuffer buffer, Graphic<T, ST> object, int count )
      {
         final Graphic<T, ST> data = (object == null) ? newInstance () : object;

         readAlarm (buffer, data);

         // GR_FLOAT and GR_DOUBLE Only
         final int dataType = getDataType();
         if ( dataType == 23 || dataType == 27 )
         {
            readPrecision(buffer, data);
         }
         readUnits( buffer, data );
         readGraphicLimits( scalarValueTypeSupport, buffer, data );

         preValuePad( buffer );
         data.setValue( valueTypeSupport.deserialize( buffer, data.getValue (), count ) );

         return data;
      }
   }

   /**-----------------------------------------------------------------------------------*
    * ControlTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static class ControlTypeSupport<T, ST> extends MetadataTypeSupport<Control<T, ST>, T>
   {
      protected final TypeSupport<ST> scalarValueTypeSupport;

      private ControlTypeSupport( TypeSupport<T> valueTypeSupport, int preValuePadding,
                                  TypeSupport<ST> scalarValueTypeSupport )
      {
         super (valueTypeSupport, preValuePadding);
         this.scalarValueTypeSupport = scalarValueTypeSupport;
      }

      @Override
      public Control<T, ST> newInstance()
      {
         return new Control<>();
      }

      @Override
      public int getDataType()
      {
         return valueTypeSupport.getDataType () + 28;
      }

      @Override
      public Control<T, ST> deserialize( ByteBuffer buffer, Control<T, ST> object, int count )
      {
         final Control<T, ST> data = (object == null) ? newInstance () : object;

         readAlarm( buffer, data );

         // CTRL_FLOAT and CTRL_DOUBLE only
         int dataType = getDataType ();
         if ( dataType == 30 || dataType == 34 )
         {
            readPrecision(buffer, data);
         }
         readUnits( buffer, data );
         readGraphicLimits( scalarValueTypeSupport, buffer, data );
         readControlLimits( scalarValueTypeSupport, buffer, data );

         preValuePad (buffer);
         data.setValue( valueTypeSupport.deserialize( buffer, data.getValue (), count ) );

         return data;
      }
   }

   /**-----------------------------------------------------------------------------------*
    * DoubleTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static final class DoubleTypeSupport implements TypeSupport<Double>
   {
      public static final DoubleTypeSupport INSTANCE = new DoubleTypeSupport ();
      private static final Double DUMMY_INSTANCE = (double) 0;

      private DoubleTypeSupport() {}

      @Override
      public Double newInstance()
      {
         return DUMMY_INSTANCE;
      }

      @Override
      public int getDataType()
      {
         return 6;
      }

      @Override
      public int getForcedElementCount()
      {
         return 1;
      }

      @Override
      public void serialize( ByteBuffer buffer, Double object, int count )
      {
         buffer.putDouble ( object );
      }

      @Override
      public int serializeSize( Double object, int count )
      {
         return 8;
      }

      @Override
      public Double deserialize( ByteBuffer buffer, Double object, int count )
      {
         return buffer.getDouble ();
      }
   }

   /**-----------------------------------------------------------------------------------*
    * DoubleArrayTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static final class DoubleArrayTypeSupport implements TypeSupport<double[]>
   {
      public static final DoubleArrayTypeSupport INSTANCE = new DoubleArrayTypeSupport ();
      private static final double[] DUMMY_INSTANCE = new double[ 0 ];

      private DoubleArrayTypeSupport() {}

      @Override
      public double[] newInstance()
      {
         return DUMMY_INSTANCE;
      }

      @Override
      public int getDataType()
      {
         return 6;
      }

      @Override
      public int serializeSize( double[] object, int count )
      {
         return 8 * count;
      }

      @Override
      public void serialize( ByteBuffer buffer, double[] object, int count )
      {
         @SuppressWarnings( "UnnecessaryLocalVariable" )
         double[] array = object;
         if ( count < OPTIMIZED_COPY_THRESHOLD )
         {
            for ( int i = 0; i < count; i++ )
               buffer.putDouble (array[ i ]);

         }
         else
         {
            buffer.asDoubleBuffer ().put (array, 0, count);
            buffer.position (buffer.position () + serializeSize (array, count));
         }
      }

      @Override
      public double[] deserialize( ByteBuffer buffer, double[] object, int count )
      {
         double[] data;
         if ( object == null )
         {
            data = new double[ count ];
         }
         else
         {
            data = object;
            if ( data.length != count )
            {
               data = new double[ count ];
            }
         }

         if ( count < OPTIMIZED_COPY_THRESHOLD )
         {
            for ( int i = 0; i < count; i++ )
            {
               data[ i ] = buffer.getDouble();
            }
         }
         else
         {
            buffer.asDoubleBuffer ().get (data, 0, count);
         }

         return data;
      }
   }

   /**-----------------------------------------------------------------------------------*
    * FloatTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static final class FloatTypeSupport implements TypeSupport<Float>
   {
      public static final FloatTypeSupport INSTANCE = new FloatTypeSupport ();
      private static final Float DUMMY_INSTANCE = (float) 0;

      private FloatTypeSupport() {}

      @Override
      public Float newInstance()
      {
         return DUMMY_INSTANCE;
      }

      @Override
      public int getDataType()
      {
         return 2;
      }

      @Override
      public int getForcedElementCount()
      {
         return 1;
      }

      @Override
      public void serialize( ByteBuffer buffer, Float object, int count )
      {
         buffer.putFloat( object) ;
      }

      @Override
      public int serializeSize( Float object, int count )
      {
         return 4;
      }

      @Override
      public Float deserialize( ByteBuffer buffer, Float object, int count )
      {
         return buffer.getFloat ();
      }
   }


   /**-----------------------------------------------------------------------------------*
    * FloatArrayTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static final class FloatArrayTypeSupport implements TypeSupport<float[]>
   {
      public static final FloatArrayTypeSupport INSTANCE = new FloatArrayTypeSupport ();
      private static final float[] DUMMY_INSTANCE = new float[ 0 ];

      private FloatArrayTypeSupport() {}

      @Override
      public float[] newInstance()
      {
         return DUMMY_INSTANCE;
      }

      @Override
      public int getDataType()
      {
         return 2;
      }

      @Override
      public int serializeSize( float[] object, int count )
      {
         return 4 * count;
      }

      @Override
      public void serialize( ByteBuffer buffer, float[] object, int count )
      {
         //noinspection UnnecessaryLocalVariable
         float[] array = object;
         if ( count < OPTIMIZED_COPY_THRESHOLD )
         {
            for ( int i = 0; i < count; i++ )
            {
               buffer.putFloat(array[ i ]);
            }

         }
         else
         {
            buffer.asFloatBuffer ().put (array, 0, count);
            buffer.position (buffer.position () + serializeSize (array, count));
         }
      }

      @Override
      public float[] deserialize( ByteBuffer buffer, float[] object, int count )
      {

         float[] data;
         if ( object == null )
         {
            data = new float[ count ];
         }
         else
         {
            data = object;
            if ( data.length != count )
            {
               data = new float[ count ];
            }
         }

         if ( count < OPTIMIZED_COPY_THRESHOLD )
         {
            for ( int i = 0; i < count; i++ )
            {
               data[ i ] = buffer.getFloat();
            }
         }
         else
         {
            buffer.asFloatBuffer ().get (data, 0, count);
         }

         return data;
      }
   }

   /**-----------------------------------------------------------------------------------*
    * ByteTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static final class ByteTypeSupport implements TypeSupport<Byte>
   {
      public static final ByteTypeSupport INSTANCE = new ByteTypeSupport ();
      private static final Byte DUMMY_INSTANCE = (byte) 0;

      private ByteTypeSupport() {}

      @Override
      public Byte newInstance()
      {
         return DUMMY_INSTANCE;
      }

      @Override
      public int getDataType()
      {
         return 4;
      }

      @Override
      public int getForcedElementCount()
      {
         return 1;
      }

      @Override
      public void serialize( ByteBuffer buffer, Byte object, int count )
      {
         buffer.put (object);
      }

      @Override
      public int serializeSize( Byte object, int count )
      {
         return 1;
      }

      @Override
      public Byte deserialize( ByteBuffer buffer, Byte object, int count )
      {
         return buffer.get ();
      }
   }


   /**-----------------------------------------------------------------------------------*
    * ByteArrayTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static final class ByteArrayTypeSupport implements TypeSupport<byte[]>
   {
      public static final ByteArrayTypeSupport INSTANCE = new ByteArrayTypeSupport ();
      private static final byte[] DUMMY_INSTANCE = new byte[ 0 ];

      private ByteArrayTypeSupport() {}

      @Override
      public byte[] newInstance()
      {
         return DUMMY_INSTANCE;
      }

      @Override
      public int getDataType()
      {
         return 4;
      }

      @Override
      public int serializeSize( byte[] object, int count )
      {
         return count;
      }

      @Override
      public void serialize( ByteBuffer buffer, byte[] object, int count )
      {
         //noinspection UnnecessaryLocalVariable
         byte[] array = object;
         if ( count < OPTIMIZED_COPY_THRESHOLD )
         {
            for ( int i = 0; i < count; i++ )
            {
               buffer.put(array[ i ]);
            }

         }
         else
         {
            buffer.put (array, 0, count);
         }
      }

      @Override
      public byte[] deserialize( ByteBuffer buffer, byte[] object, int count )
      {

         byte[] data;
         if ( object == null )
         {
            data = new byte[ count ];
         }
         else
         {
            data = object;
            if ( data.length != count )
            {
               data = new byte[ count ];
            }
         }

         if ( count < OPTIMIZED_COPY_THRESHOLD )
         {
            for ( int i = 0; i < count; i++ )
            {
               data[ i ] = buffer.get();
            }
         }
         else
         {
            buffer.get (data, 0, count);
         }

         return data;
      }
   }


   /**-----------------------------------------------------------------------------------*
    * ShortTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static final class ShortTypeSupport implements TypeSupport<Short>
   {
      public static final ShortTypeSupport INSTANCE = new ShortTypeSupport ();
      public static final Short DUMMY_INSTANCE = (short) 0;

      private ShortTypeSupport() {}

      @Override
      public Short newInstance()
      {
         return DUMMY_INSTANCE;
      }

      @Override
      public int getDataType()
      {
         return 1;
      }

      @Override
      public int getForcedElementCount()
      {
         return 1;
      }

      @Override
      public int serializeSize( Short object, int count )
      {
         return 2;
      }

      @Override
      public void serialize( ByteBuffer buffer, Short object, int count )
      {
         buffer.putShort (object);
      }

      @Override
      public Short deserialize( ByteBuffer buffer, Short object, int count )
      {
         return buffer.getShort ();
      }
   }


   /**-----------------------------------------------------------------------------------*
    * ShortArrayTypeSupport
    *-----------------------------------------------------------------------------------*/
   @SuppressWarnings( "UnnecessaryLocalVariable" )
   private static final class ShortArrayTypeSupport implements TypeSupport<short[]>
   {
      public static final ShortArrayTypeSupport INSTANCE = new ShortArrayTypeSupport ();
      private static final short[] DUMMY_INSTANCE = new short[ 0 ];

      private ShortArrayTypeSupport() {}

      @Override
      public short[] newInstance()
      {
         return DUMMY_INSTANCE;
      }

      @Override
      public int getDataType()
      {
         return 1;
      }

      @Override
      public int serializeSize( short[] object, int count )
      {
         return 2 * count;
      }

      @Override
      public void serialize( ByteBuffer buffer, short[] object, int count )
      {
         short[] array = object;
         if ( count < OPTIMIZED_COPY_THRESHOLD )
         {
            for ( int i = 0; i < count; i++ )
            {
               buffer.putShort(array[ i ]);
            }
         }
         else
         {
            buffer.asShortBuffer ().put (array, 0, count);
            buffer.position (buffer.position () + serializeSize (array, count));
         }
      }

      @Override
      public short[] deserialize( ByteBuffer buffer, short[] object, int count )
      {
         short[] data;
         if ( object == null )
         {
            data = new short[ count ];
         }
         else
         {
            data = object;
            if ( data.length != count )
            {
               data = new short[ count ];
            }
         }

         if ( count < OPTIMIZED_COPY_THRESHOLD )
         {
            for ( int i = 0; i < count; i++ )
            {
               data[ i ] = buffer.getShort();
            }
         }
         else
         {
            buffer.asShortBuffer ().get (data, 0, count);
         }

         return data;
      }
   }


   /**-----------------------------------------------------------------------------------*
    * GraphicEnumTypeSupport
    *-----------------------------------------------------------------------------------*/

   @SuppressWarnings( "DuplicatedCode" )
   private static final class GraphicEnumTypeSupport implements TypeSupport<GraphicEnum>
   {
      public static final GraphicEnumTypeSupport INSTANCE = new GraphicEnumTypeSupport ();

      private GraphicEnumTypeSupport() {}

      @Override
      public GraphicEnum newInstance()
      {
         return new GraphicEnum ();
      }

      @Override
      public int getDataType()
      {
         return 24;
      }

      @Override
      public int getForcedElementCount()
      {
         return 1;
      }

      @Override
      public GraphicEnum deserialize( ByteBuffer buffer, GraphicEnum object, int count )
      {
         final GraphicEnum data = (object == null) ? newInstance() : object;

         readAlarm (buffer, data);

         final int MAX_ENUM_STRING_SIZE = 26;
         final int MAX_ENUM_STATES = 16;
         final int n = buffer.getShort () & 0xFFFF;

         byte[] rawBuffer = new byte[ MAX_ENUM_STRING_SIZE ];

         // read labels
         final String[] labels = new String[ n ];
         for ( int i = 0; i < n; i++ )
         {
            buffer.get (rawBuffer);
            labels[ i ] = extractString (rawBuffer);
         }

         // read rest
         final int restEntries = MAX_ENUM_STATES - n;
         for ( int i = 0; i < restEntries; i++ )
         {
            buffer.get(rawBuffer);
         }

         data.setLabels( labels );
         data.setValue( ShortTypeSupport.INSTANCE.deserialize ( buffer, data.getValue (), count ) );

         return data;
      }
   }

   /**-----------------------------------------------------------------------------------*
    * GraphicEnumArrayTypeSupport
    *-----------------------------------------------------------------------------------*/

   @SuppressWarnings( "DuplicatedCode" )
   private static final class GraphicEnumArrayTypeSupport implements TypeSupport<GraphicEnumArray>
   {
      public static final GraphicEnumArrayTypeSupport INSTANCE = new GraphicEnumArrayTypeSupport ();

      private GraphicEnumArrayTypeSupport() {}

      @Override
      public GraphicEnumArray newInstance()
      {
         return new GraphicEnumArray ();
      }

      @Override
      public int getDataType()
      {
         return 24;
      }

      @Override
      public GraphicEnumArray deserialize( ByteBuffer buffer, GraphicEnumArray object, int count )
      {
         GraphicEnumArray data = (object == null) ? newInstance () : object;

         readAlarm (buffer, data);

         final int MAX_ENUM_STRING_SIZE = 26;
         final int MAX_ENUM_STATES = 16;

         final int n = buffer.getShort () & 0xFFFF;
         final byte[] rawBuffer = new byte[ MAX_ENUM_STRING_SIZE ];

         // read labels
         final String[] labels = new String[ n ];
         for ( int i = 0; i < n; i++ )
         {
            buffer.get (rawBuffer);
            labels[ i ] = extractString (rawBuffer);
         }

         // read rest
         final int restEntries = MAX_ENUM_STATES - n;
         for ( int i = 0; i < restEntries; i++ )
         {
            buffer.get( rawBuffer );
         }

         data.setLabels (labels);
         data.setValue (ShortArrayTypeSupport.INSTANCE.deserialize (buffer, data.getValue (), count));

         return data;
      }
   }


   /**-----------------------------------------------------------------------------------*
    * IntegerTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static final class IntegerTypeSupport implements TypeSupport<Integer>
   {
      public static final IntegerTypeSupport INSTANCE = new IntegerTypeSupport ();
      public static final Integer DUMMY_INSTANCE = 0;

      private IntegerTypeSupport() {}

      @Override
      public Integer newInstance()
      {
         return DUMMY_INSTANCE;
      }

      @Override
      public int getDataType()
      {
         return 5;
      }

      @Override
      public int getForcedElementCount()
      {
         return 1;
      }

      @Override
      public int serializeSize( Integer object, int count )
      {
         return 4;
      }

      @Override
      public void serialize( ByteBuffer buffer, Integer object, int count )
      {
         buffer.putInt (object);
      }

      @Override
      public Integer deserialize( ByteBuffer buffer, Integer object, int count )
      {
         return buffer.getInt ();
      }
   }


   /**-----------------------------------------------------------------------------------*
    * IntegerArrayTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static final class IntegerArrayTypeSupport implements TypeSupport<int[]>
   {
      public static final IntegerArrayTypeSupport INSTANCE = new IntegerArrayTypeSupport();
      private static final int[] DUMMY_INSTANCE = new int[ 0 ];

      private IntegerArrayTypeSupport() {}

      @Override
      public int[] newInstance()
      {
         return DUMMY_INSTANCE;
      }

      @Override
      public int getDataType()
      {
         return 5;
      }

      @Override
      public int serializeSize( int[] object, int count )
      {
         return 4 * count;
      }

      @Override
      public void serialize( ByteBuffer buffer, int[] object, int count )
      {
         //noinspection UnnecessaryLocalVariable
         final int[] array = object;
         if ( count < OPTIMIZED_COPY_THRESHOLD )
         {
            for ( int i = 0; i < count; i++ )
            {
               buffer.putInt(array[ i ]);
            }
         }
         else
         {
            buffer.asIntBuffer ().put (array, 0, count);
            buffer.position (buffer.position () + serializeSize (array, count));
         }
      }

      @Override
      public int[] deserialize( ByteBuffer buffer, int[] object, int count )
      {

         int[] data;
         if ( object == null )
         {
            data = new int[ count ];
         }
         else
         {
            data = object;
            if ( data.length != count )
            {
               data = new int[ count ];
            }
         }

         if ( count < OPTIMIZED_COPY_THRESHOLD )
         {
            for ( int i = 0; i < count; i++ )
            {
               data[ i ] = buffer.getInt();
            }
         }
         else
         {
            buffer.asIntBuffer ().get (data, 0, count);
         }

         return data;
      }
   }


   /**-----------------------------------------------------------------------------------*
    * StringTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static final class StringTypeSupport implements TypeSupport<String>
   {
      public static final StringTypeSupport INSTANCE = new StringTypeSupport ();
      public static final String DUMMY_INSTANCE = "";

      private StringTypeSupport() {}

      @Override
      public String newInstance()
      {
         return DUMMY_INSTANCE;
      }

      @Override
      public int getDataType()
      {
         return 0;
      }

      @Override
      public int getForcedElementCount()
      {
         return 1;
      }

      @Override
      public int serializeSize( String object, int count )
      {
         return object.length () + 1;
      }

      @Override
      public void serialize( ByteBuffer buffer, String object, int count )
      {
         buffer.put (object.getBytes ());
         buffer.put ((byte) 0);
      }

      @Override
      public String deserialize( ByteBuffer buffer, String object, int count )
      {
         final int start = buffer.position ();
         final int bufferEnd = buffer.limit ();
         int end = start;

         // find zero char (string terminator)
         while ( buffer.get (end) != 0 && end < bufferEnd )
         {
            end++;
         }

         // If the buffer is array backed, we can simply
         // use it directly. If not, we need to make a copy
         if ( buffer.hasArray () )
         {
            // NOTE: rest of the bytes are left in the buffer
            return new String (buffer.array (), start, end - start);
         }

         final int length = end - start;
         final byte[] data = new byte[ length ];
         if ( length < OPTIMIZED_COPY_THRESHOLD )
         {
            for ( int i = 0; i < length; i++ )
            {
               data[ i ] = buffer.get();
            }
         }
         else
         {
            buffer.get (data, 0, length);
         }
         return new String (data, 0, length);

      }
   }

   /**-----------------------------------------------------------------------------------*
    * StringArrayTypeSupport
    *-----------------------------------------------------------------------------------*/

   private static final class StringArrayTypeSupport implements TypeSupport<String[]>
   {
      private static final int MAX_STRING_SIZE = 40;
      public static final StringArrayTypeSupport INSTANCE = new StringArrayTypeSupport ();
      private static final String[] DUMMY_INSTANCE = new String[ 0 ];

      private StringArrayTypeSupport() {}

      @Override
      public String[] newInstance()
      {
         return DUMMY_INSTANCE;
      }

      @Override
      public int getDataType()
      {
         return 0;
      }

      @Override
      public int serializeSize( String[] object, int count )
      {
         if ( count == 1 )
         {
            return StringTypeSupport.INSTANCE.serializeSize(object[ 0 ], 1);
         }
         else
         {
            return MAX_STRING_SIZE * count;
         }
      }

      @Override
      public void serialize( ByteBuffer buffer, String[] object, int count )
      {
         if ( count == 1 )
         {
            StringTypeSupport.INSTANCE.serialize(buffer, object[ 0 ], 1);
         }
         else
         {
            for ( int i = 0; i < count; i++ )
            {
               // limit string size, leave one byte for termination
               final int pos = buffer.position ();
               if ( object[ i ] != null )
               {
                  int bytesToWrite = Math.min (object[ i ].length (), MAX_STRING_SIZE - 1);
                  buffer.put (object[ i ].getBytes (), 0, bytesToWrite);
               }
               buffer.put ((byte) 0);
               buffer.position (pos + MAX_STRING_SIZE);
            }

         }
      }

      @Override
      public String[] deserialize( ByteBuffer buffer, String[] object, int count )
      {

         String[] data;
         if ( object == null )
         {
            data = new String[ count ];
         }
         else
         {
            data = object;
            if ( data.length != count )
            {
               data = new String[ count ];
            }
         }

         if ( count == 1 )
         {
            data[ 0 ] = StringTypeSupport.INSTANCE.deserialize (buffer, null, 1);
         }
         else
         {
            byte[] rawBuffer = new byte[ MAX_STRING_SIZE ];
            for ( int i = 0; i < count; i++ )
            {
               buffer.get (rawBuffer);
               data[ i ] = extractString (rawBuffer);
            }
         }

         return data;
      }
   }

   /**-----------------------------------------------------------------------------------*
    * Initialise Data Structures
    *-----------------------------------------------------------------------------------*/

   static final Set<Class<?>> nativeTypeSet;
   static final Map<Class<?>, Map<Class<?>, TypeSupport<?>>> typeSupportMap;

   static
   {
      final Set<Class<?>> set = new HashSet<>();

      set.add( String.class );
      set.add( Short.class );
      set.add( Float.class );
      set.add( Byte.class );
      set.add( Integer.class );
      set.add( Double.class );

      set.add( String[].class );
      set.add( short[].class );
      set.add( float[].class );
      set.add( byte[].class );
      set.add( int[].class );
      set.add( double[].class );

      // enum is short
      nativeTypeSet = Collections.unmodifiableSet( set );

      final Map<Class<?>, Map<Class<?>, TypeSupport<?>>> rootMap = new HashMap<> ();

      //
      // native type support (metaType class == Void.class)
      //
      {
         final Map<Class<?>, TypeSupport<?>> map = new HashMap<> ();
         map.put( String.class, StringTypeSupport.INSTANCE);
         map.put( Short.class, ShortTypeSupport.INSTANCE);
         map.put( Float.class, FloatTypeSupport.INSTANCE);
         map.put( Byte.class, ByteTypeSupport.INSTANCE);
         map.put( Integer.class, IntegerTypeSupport.INSTANCE);
         map.put( Double.class, DoubleTypeSupport.INSTANCE);

         map.put( String[].class, StringArrayTypeSupport.INSTANCE);
         map.put( short[].class, ShortArrayTypeSupport.INSTANCE);
         map.put( float[].class, FloatArrayTypeSupport.INSTANCE);
         map.put( byte[].class, ByteArrayTypeSupport.INSTANCE);
         map.put( int[].class, IntegerArrayTypeSupport.INSTANCE);
         map.put(double[].class, DoubleArrayTypeSupport.INSTANCE);

         rootMap.put (Void.class, Collections.unmodifiableMap (map));
      }

      //
      // STS type support (metaType class == Alarm.class)
      //
      {
         final Map<Class<?>, TypeSupport<?>> map = new HashMap<> ();
         map.put( String.class, new STSTypeSupport<> (StringTypeSupport.INSTANCE, 0));
         map.put( Short.class, new STSTypeSupport<> (ShortTypeSupport.INSTANCE, 0));
         map.put( Float.class, new STSTypeSupport<> (FloatTypeSupport.INSTANCE, 0));
         map.put( Byte.class, new STSTypeSupport<> (ByteTypeSupport.INSTANCE, 1));
         map.put( Integer.class, new STSTypeSupport<> (IntegerTypeSupport.INSTANCE, 0));
         map.put( Double.class, new STSTypeSupport<> (DoubleTypeSupport.INSTANCE, 4));

         map.put( String[].class, new STSTypeSupport<> (StringArrayTypeSupport.INSTANCE, 0));
         map.put( short[].class, new STSTypeSupport<> (ShortArrayTypeSupport.INSTANCE, 0));
         map.put( float[].class, new STSTypeSupport<> (FloatArrayTypeSupport.INSTANCE, 0));
         map.put( byte[].class, new STSTypeSupport<> (ByteArrayTypeSupport.INSTANCE, 1));
         map.put( int[].class, new STSTypeSupport<> (IntegerArrayTypeSupport.INSTANCE, 0));
         map.put( double[].class, new STSTypeSupport<> (DoubleArrayTypeSupport.INSTANCE, 4));

         rootMap.put( Alarm.class, Collections.unmodifiableMap (map));
      }

      //
      // TIME type support (metaType class == Timestamped.class)
      //
      {
         final Map<Class<?>, TypeSupport<?>> map = new HashMap<> ();
         map.put( String.class, new TimeTypeSupport<> (StringTypeSupport.INSTANCE, 0));
         map.put( Short.class, new TimeTypeSupport<> (ShortTypeSupport.INSTANCE, 2));
         map.put( Float.class, new TimeTypeSupport<> (FloatTypeSupport.INSTANCE, 0));
         map.put( Byte.class, new TimeTypeSupport<> (ByteTypeSupport.INSTANCE, 3));
         map.put( Integer.class, new TimeTypeSupport<> (IntegerTypeSupport.INSTANCE, 0));
         map.put( Double.class, new TimeTypeSupport<> (DoubleTypeSupport.INSTANCE, 4));

         map.put( String[].class, new TimeTypeSupport<> (StringArrayTypeSupport.INSTANCE, 0));
         map.put( short[].class, new TimeTypeSupport<> (ShortArrayTypeSupport.INSTANCE, 2));
         map.put( float[].class, new TimeTypeSupport<> (FloatArrayTypeSupport.INSTANCE, 0));
         map.put( byte[].class, new TimeTypeSupport<> (ByteArrayTypeSupport.INSTANCE, 3));
         map.put( int[].class, new TimeTypeSupport<> (IntegerArrayTypeSupport.INSTANCE, 0));
         map.put( double[].class, new TimeTypeSupport<> (DoubleArrayTypeSupport.INSTANCE, 4));

         rootMap.put( Timestamped.class, Collections.unmodifiableMap (map));
      }

      //
      // GRAPHIC type support (metaType class == Graphic.class)
      //
      {
         final Map<Class<?>, TypeSupport<?>> map = new HashMap<> ();
         // there is no real Graphic<String>
         map.put( Short.class, new GraphicTypeSupport<> (ShortTypeSupport.INSTANCE, 0, ShortTypeSupport.INSTANCE));
         map.put( Float.class, new GraphicTypeSupport<> (FloatTypeSupport.INSTANCE, 0, FloatTypeSupport.INSTANCE));
         map.put( Byte.class, new GraphicTypeSupport<> (ByteTypeSupport.INSTANCE, 1, ByteTypeSupport.INSTANCE));
         map.put( Integer.class, new GraphicTypeSupport<> (IntegerTypeSupport.INSTANCE, 0, IntegerTypeSupport.INSTANCE));
         map.put( Double.class, new GraphicTypeSupport<> (DoubleTypeSupport.INSTANCE, 0, DoubleTypeSupport.INSTANCE));

         // there is no real Graphic<String[]>
         map.put( short[].class, new GraphicTypeSupport<> (ShortArrayTypeSupport.INSTANCE, 0, ShortTypeSupport.INSTANCE));
         map.put( float[].class, new GraphicTypeSupport<> (FloatArrayTypeSupport.INSTANCE, 0, FloatTypeSupport.INSTANCE));
         map.put( byte[].class, new GraphicTypeSupport<> (ByteArrayTypeSupport.INSTANCE, 1, ByteTypeSupport.INSTANCE));
         map.put( int[].class, new GraphicTypeSupport<> (IntegerArrayTypeSupport.INSTANCE, 0, IntegerTypeSupport.INSTANCE));
         map.put( double[].class, new GraphicTypeSupport<> (DoubleArrayTypeSupport.INSTANCE, 0, DoubleTypeSupport.INSTANCE));

         rootMap.put (Graphic.class, Collections.unmodifiableMap (map));
      }

      //
      // CONTROL type support (metaType class == Control.class)
      //
      {
         final Map<Class<?>, TypeSupport<?>> map = new HashMap<> ();
         // there is no real Control<String>
         map.put( Short.class, new ControlTypeSupport<> (ShortTypeSupport.INSTANCE, 0, ShortTypeSupport.INSTANCE));
         map.put( Float.class, new ControlTypeSupport<> (FloatTypeSupport.INSTANCE, 0, FloatTypeSupport.INSTANCE));
         map.put( Byte.class, new ControlTypeSupport<> (ByteTypeSupport.INSTANCE, 1, ByteTypeSupport.INSTANCE));
         map.put( Integer.class, new ControlTypeSupport<> (IntegerTypeSupport.INSTANCE, 0, IntegerTypeSupport.INSTANCE));
         map.put( Double.class, new ControlTypeSupport<> (DoubleTypeSupport.INSTANCE, 0, DoubleTypeSupport.INSTANCE));

         // there is no real Control<String[]>
         map.put( short[].class, new ControlTypeSupport<> (ShortArrayTypeSupport.INSTANCE, 0, ShortTypeSupport.INSTANCE));
         map.put( float[].class, new ControlTypeSupport<> (FloatArrayTypeSupport.INSTANCE, 0, FloatTypeSupport.INSTANCE));
         map.put( byte[].class, new ControlTypeSupport<> (ByteArrayTypeSupport.INSTANCE, 1, ByteTypeSupport.INSTANCE));
         map.put( int[].class, new ControlTypeSupport<> (IntegerArrayTypeSupport.INSTANCE, 0, IntegerTypeSupport.INSTANCE));
         map.put( double[].class, new ControlTypeSupport<> (DoubleArrayTypeSupport.INSTANCE, 0, DoubleTypeSupport.INSTANCE));

         rootMap.put (Control.class, Collections.unmodifiableMap (map));
      }

      typeSupportMap = Collections.unmodifiableMap( rootMap );
   }

   private static boolean matches( TypeSupport<?> typeSupport, short typeCode, int elementCount )
   {
      if ( typeSupport.getDataType() != typeCode )
      {
         return false;
      }

      return ( elementCount > 1 ) ? (typeSupport.getForcedElementCount() == 0) : (typeSupport.getForcedElementCount() == 1);
   }

}
