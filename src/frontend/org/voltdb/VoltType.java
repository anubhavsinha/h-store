/* This file is part of VoltDB.
 * Copyright (C) 2008-2010 VoltDB L.L.C.
 *
 * VoltDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VoltDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb;
import java.math.BigDecimal;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.voltdb.types.TimestampType;

/**
 * Represents a type in a VoltDB Stored Procedure or {@link VoltTable VoltTable}.
 * Note that types in the database don't map 1-1 with types in the
 * Stored Procedure API. Varchars, Chars and Char Arrays in the DB
 * map to Strings in the API.
 */
public enum VoltType {

    /**
     * Used for uninitialized types in some places. Not a valid value
     * for actual user data.
     */
    INVALID   ((byte)0, -1, null, new Class[] {}),

    /**
     * Used to type java null values that have no type. Not a valid value
     * for actual user data.
     */
    NULL      ((byte)1, -1, null, new Class[] {}),

    /**
     * Used for some literal constants parsed by our SQL parser. Not a
     * valid value for actual user data. See {@link #DECIMAL} for decimal
     * type.
     */
    NUMERIC   ((byte)2, 0, null, new Class[] {}),

    /**
     * 1-byte signed 2s-compliment byte.
     * Lowest value means NULL in the database.
     */
    TINYINT   ((byte)3, 1, "tinyint", new Class[] {byte.class, Byte.class}),

    /**
     * 2-byte signed 2s-compliment short.
     * Lowest value means NULL in the database.
     */
    SMALLINT  ((byte)4, 2, "smallint", new Class[] {short.class, Short.class}),

    /**
     * 4-byte signed 2s-compliment integer.
     * Lowest value means NULL in the database.
     */
    INTEGER   ((byte)5, 4, "integer",
               new Class[] {int.class, Integer.class, AtomicInteger.class}),

    /**
     * 8-byte signed 2s-compliment long.
     * Lowest value means NULL in the database.
     */
    BIGINT    ((byte)6, 8, "bigint",
               new Class[] {long.class, Long.class, AtomicLong.class}),

    /**
     * 8-bytes in IEEE 754 "double format".
     * Some NaN values may represent NULL in the database (TBD).
     */
    FLOAT     ((byte)8, 8, "float",
            new Class[] {double.class, Double.class, float.class, Float.class}),

    /**
     * 8-byte long value representing milliseconds after the epoch.
     * The epoch is Jan. 1 1970 00:00:00 GMT. Negative values represent
     * time before the epoch. This covers roughly 4000BC to 8000AD.
     */
    TIMESTAMP ((byte)11, 8, "timestamp", new Class[] {TimestampType.class, Date.class}),

    /**
     * UTF-8 string with up to 32K chars.
     * The database supports char arrays and varchars
     * but the API uses strings.
     */
    STRING    ((byte)9, -1, "varchar",
               new Class[] {String.class, byte[].class, Byte[].class}),

    /**
     * VoltTable type for Procedure parameters
     */
    VOLTTABLE ((byte)21, -1, null, new Class[] {VoltTable.class}),

    /**
     * Fixed precision=38, scale=12 storing sign and null-status in a preceding byte
     */
    DECIMAL  ((byte)22, 16, "decimal", new Class[] {BigDecimal.class}),

    /**
     * Fixed precision=38, scale=12 string representation of a decimal
     */
    DECIMAL_STRING  ((byte)23, 9, "decimal", new Class[] {}),
    
    /**
     * Boolean hack...
     */
    BOOLEAN  ((byte)24, 1, "boolean", new Class[] {boolean.class, Boolean.class});

    /**
     * Size in bytes of the maximum length for a VoltDB field value, presumably a string or blob
     */
    public static final int MAX_VALUE_LENGTH = 1048576;
    public static final String MAX_VALUE_LENGTH_STR = String.valueOf(MAX_VALUE_LENGTH / 1024) + "k";

    /**
     * Fixed precision 8-byte value with 4 decimal places of precision.
     * Stored as an 8-byte long value representing 10,000x the actual value.
     */
    //MONEY     ((byte)20, 8, "money", new Class[] {});

    private final byte m_val;
    private final int m_lengthInBytes;
    private final String m_sqlString;
    private final Class<?>[] m_classes;

    private VoltType(byte val, int lengthInBytes,
            String sqlString, Class<?>[] classes) {
        m_val = val;
        m_lengthInBytes = lengthInBytes;
        m_sqlString = sqlString;
        m_classes = classes;
    }

    private static Map<Class<?>, VoltType> s_classes;
    static {
        s_classes = new HashMap<Class<?>, VoltType>();
        for (VoltType type : values()) {
            for (Class<?> cls : type.m_classes) {
                s_classes.put(cls, type);
            }
        }
    }

    protected static final VoltType idx_lookup[] = new VoltType[VoltType.BOOLEAN.m_val+1];
    protected static final Map<String, VoltType> name_lookup = new HashMap<String, VoltType>();
    static {
        for (VoltType vt : EnumSet.allOf(VoltType.class)) {
            VoltType.idx_lookup[vt.m_val] = vt;
            VoltType.name_lookup.put(vt.name().toLowerCase().intern(), vt);
        }
    }
    
    /**
     * Gets the byte that corresponds to the enum value (for serialization).
     * @return A byte representing the enum value
     */
    public byte getValue() {
        return m_val;
    }

    /**
     * Return the java class that is matched to a given <tt>VoltType</tt>.
     * @return A java class object.
     * @throws RuntimeException if a type doesn't have an associated class,
     * such as {@link #INVALID}.
     * @see #typeFromClass
     */
    public Class<?> classFromType() {
        if (m_classes.length == 0) {
            throw new RuntimeException("Unsupported type " + this);
        }
        return m_classes[0];
    }

    /**
     * Statically create an enum value from the corresponding byte.
     * @param val A byte representing an enum value
     * @return The appropriate enum value
     */
//    public static VoltType get(byte val) {
//        assert(val < idx_lookup.length) : "Unknown type: " + val;
//        VoltType type = idx_lookup[val];
//        assert(type != null) : "Unknown type: " + val;
//        return (type);
//    }
    public static VoltType get(int val) {
        assert(val < idx_lookup.length) : "Unknown type: " + val;
        VoltType type = idx_lookup[val];
        assert(type != null) : "Unknown type: " + val;
        return (type);
    }

    private boolean matchesString(String str) {
        return str.endsWith(name());
    }

    /**
     * Converts string representations to an enum value.
     * @param str A string in the form "VoltType.TYPENAME"
     * @return One of the valid enum values for VoltType
     */
    public static VoltType typeFromString(String str) {
        for (VoltType type: values()) {
            if (type.matchesString(str)) {
                return type;
            }
        }
        if (str.equals("DECIMAL")) return DECIMAL;
        if (str.endsWith("DOUBLE")) return FLOAT;
        // if (str.endsWith("VARCHAR")) return STRING;
        if (str.endsWith("CHAR")) return STRING;

        throw new RuntimeException("Can't find type: " + str);
    }

    /**
     * Ascertain the most appropriate <tt>VoltType</tt> given a
     * java object.
     * @param obj The java object to type.
     * @return A <tt>VoltType</tt> or invalid if none applies.
     * @see #typeFromClass
     */
    public static VoltType typeFromObject(Object obj) {
        assert obj != null;

        Class<?> cls = obj.getClass();
        return typeFromClass(cls);
    }

    /**
     * Ascertain the most appropriate <tt>VoltType</tt> given a
     * java class.
     * @param cls The java class to type.
     * @return A <tt>VoltType</tt> or invalid if none applies.
     * @see #typeFromObject
     * @see #classFromType
     */
    public static VoltType typeFromClass(Class<?> cls) {
        VoltType type = s_classes.get(cls);
        if (type == null) {
            throw new VoltTypeException("Unimplemented Object Type: " + cls);
        }
        return type;
    }

    /**
     * Return the string representation of this type. Note that
     * <tt>VoltType.typeFromString(voltTypeInstance.toString) == true</tt>.
     * @return The string representation of this type.
     */
    @Override public String toString() {
        return "VoltType." + name();
    }

    public int getLengthInBytesForFixedTypes() {
        if (m_lengthInBytes == -1) {
            throw new RuntimeException(
                    "Asking for fixed size for non-fixed or unknown type.");
        }
        return m_lengthInBytes;
    }

    /**
     * Get the corresponding SQL type as for a given <tt>VoltType</tt> enum.
     * For example, {@link #STRING} will probably convert to "VARCHAR".
     * @return A string representing the SQL type.
     */
    public String toSQLString() {
        return m_sqlString;
    }

    // Really hacky cast overflow detection for primitive types
    // Comparison to MIN_VALUEs are <= to avoid collisions with the NULL
    // bit pattern
    // Probably eventually want a generic wouldCastDiscardInfo() call or
    // something
    boolean wouldCastOverflow(Number value)
    {
        boolean retval = false;
        switch (this)
        {
        case TINYINT:
            if (value.longValue() <= Byte.MIN_VALUE ||
                    value.longValue() > Byte.MAX_VALUE)
            {
                retval = true;
            }
            break;
        case SMALLINT:
            if (value.longValue() <= Short.MIN_VALUE ||
                    value.longValue() > Short.MAX_VALUE)
            {
                retval = true;
            }
            break;
        case INTEGER:
            if (value.longValue() <= Integer.MIN_VALUE ||
                    value.longValue() > Integer.MAX_VALUE)
            {
                retval = true;
            }
            break;
        case BIGINT:
            // overflow isn't detectable for Longs, just look for NULL value
            // In practice, I believe that we should never get here in VoltTable
            // since we check for NULL before checking for cast overflow
            if (value.longValue() == NULL_BIGINT)
            {
                retval = true;
            }
            break;
        case FLOAT:
            // this really should never occur, also, just look for NULL
            // In practice, I believe that we should never get here in VoltTable
            // since we check for NULL before checking for cast overflow
            if (value.doubleValue() == NULL_FLOAT)
            {
                retval = true;
            }
            break;
        default:
            throw new VoltTypeException("Unhandled cast overflow case, " +
                                        "casting to: " + toString());
        }
        return retval;
    }

    // XXX I feel like it should be possible to jam this into the enum
    // constructor somehow but java hates me when I move constant definitions
    // above the enum constructors, so, meh

    /**
     * Get a value representing whichever null value is appropriate for
     * the current <tt>VoltType</tt> enum. For example, if this type is
     * {@link #TINYINT}, this will return a java <tt>byte</tt> with value
     * -128, which is the constant NULL_TINYINT in VoltDB.
     * @return A new final instance with value equal to null for a given
     * type.
     */
    public Object getNullValue()
    {
        switch (this)
        {
        case TINYINT:
        case BOOLEAN:
            return NULL_TINYINT;
        case SMALLINT:
            return NULL_SMALLINT;
        case INTEGER:
            return NULL_INTEGER;
        case BIGINT:
            return NULL_BIGINT;
        case FLOAT:
            return NULL_FLOAT;
        case STRING:
            return NULL_STRING;
        case TIMESTAMP:
            return NULL_TIMESTAMP;
        case DECIMAL:
            return NULL_DECIMAL;
        default:
            throw new VoltTypeException("No NULL value for " + toString());
        }
    }

    static boolean isNullVoltType(Object obj)
    {
        boolean retval = false;
        if (obj == null)
        {
            retval = true;
        }
        else if (obj == VoltType.NULL_TIMESTAMP ||
                obj == VoltType.NULL_STRING ||
                obj == VoltType.NULL_DECIMAL)
        {
            retval = true;
        }
        else
        {
            switch(typeFromObject(obj))
            {
            case BOOLEAN:
                retval = false; // HACK
                break;
            case TINYINT:
                retval = (((Number) obj).byteValue() == NULL_TINYINT);
                break;
            case SMALLINT:
                retval = (((Number) obj).shortValue() == NULL_SMALLINT);
                break;
            case INTEGER:
                retval = (((Number) obj).intValue() == NULL_INTEGER);
                break;
            case BIGINT:
                retval = (((Number) obj).longValue() == NULL_BIGINT);
                break;
            case FLOAT:
                retval = (((Number) obj).doubleValue() == NULL_FLOAT);
                break;
            case TIMESTAMP:
                retval = (obj == VoltType.NULL_TIMESTAMP);
                break;
            case STRING:
                retval = (obj == VoltType.NULL_STRING);
                break;
            case DECIMAL:
                retval = (obj == VoltType.NULL_DECIMAL);
                break;
            default:
                throw new VoltTypeException("Unsupported type: " +
                                            typeFromObject(obj));
            }
        }
        return retval;
    }

    /**
     * Is the type a number and is it an exact value (no rounding errors)?
     * @return true for integers and decimals. False for floats and strings
     * and anything else.
     */
    public boolean isExactNumeric() {
        switch(this)  {
        case TINYINT:
        case SMALLINT:
        case INTEGER:
        case BIGINT:
        case DECIMAL:
            return true;
        default:
            return false;
        }
    }

    public static final int NULL_STRING_LENGTH = -1;
    public static final byte NULL_TINYINT = Byte.MIN_VALUE;
    public static final short NULL_SMALLINT = Short.MIN_VALUE;
    public static final int NULL_INTEGER = Integer.MIN_VALUE;
    public static final long NULL_BIGINT = Long.MIN_VALUE;
    // TODO(evanj): make this a specific bit pattern?
    public static final double NULL_FLOAT = -1.7E+308;
    public static final Double NULL_DOUBLE = new Double(-1.7976931348623157E+308);

    // for consistency at the API level, provide symbolic nulls for these types, too
    private static final class NullTimestampSigil{}
    public static final NullTimestampSigil NULL_TIMESTAMP = new NullTimestampSigil();

    private static final class NullStringSigil{}
    public static final NullStringSigil NULL_STRING = new NullStringSigil();

    private static final class NullDecimalSigil{}
    public static final NullDecimalSigil NULL_DECIMAL = new NullDecimalSigil();
}
