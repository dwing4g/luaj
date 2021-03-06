package org.luaj.vm2.lib;

import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaDouble;
import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Helper class to coerce values from Java to lua within the luajava library.
 * <p>
 * This class is primarily used by the {@link LibLuajava},
 * but can also be used directly when working with Java/lua bindings.
 * <p>
 * To coerce scalar types, the various, generally the {@code valueOf(type)} methods
 * on {@link LuaValue} may be used:
 * <ul>
 * <li>{@link LuaValue#valueOf(boolean)}</li>
 * <li>{@link LuaValue#valueOf(byte[])}</li>
 * <li>{@link LuaValue#valueOf(double)}</li>
 * <li>{@link LuaValue#valueOf(int)}</li>
 * <li>{@link LuaValue#valueOf(String)}</li>
 * </ul>
 * <p>
 * To coerce arrays of objects and lists, the {@code listOf(..)} and {@code tableOf(...)} methods
 * on {@link LuaValue} may be used:
 * <ul>
 * <li>{@link LuaValue#listOf(LuaValue[])}</li>
 * <li>{@link LuaValue#listOf(LuaValue[], Varargs)}</li>
 * <li>{@link LuaValue#tableOf(LuaValue[])}</li>
 * <li>{@link LuaValue#tableOf(LuaValue[], LuaValue[], Varargs)}</li>
 * </ul>
 * The method {@link CoerceJavaToLua#coerce(Object)} looks as the type and dimesioning
 * of the argument and tries to guess the best fit for corrsponding lua scalar,
 * table, or table of tables.
 *
 * @see CoerceJavaToLua#coerce(Object)
 * @see LibLuajava
 */
public final class CoerceJavaToLua
{
	static interface Coercion
	{
		public LuaValue coerce(Object javaValue);
	}

	private static final ConcurrentHashMap<Class<?>, Coercion> COERCIONS        = new ConcurrentHashMap<Class<?>, Coercion>();

	static
	{
		Coercion boolCoercion = new Coercion()
		{
			@Override
			public LuaValue coerce(Object javaValue)
			{
				Boolean b = (Boolean)javaValue;
				return b.booleanValue() ? LuaValue.TRUE : LuaValue.FALSE;
			}
		};
		Coercion intCoercion = new Coercion()
		{
			@Override
			public LuaValue coerce(Object javaValue)
			{
				Number n = (Number)javaValue;
				return LuaInteger.valueOf(n.intValue());
			}
		};
		Coercion charCoercion = new Coercion()
		{
			@Override
			public LuaValue coerce(Object javaValue)
			{
				Character c = (Character)javaValue;
				return LuaInteger.valueOf(c.charValue());
			}
		};
		Coercion doubleCoercion = new Coercion()
		{
			@Override
			public LuaValue coerce(Object javaValue)
			{
				Number n = (Number)javaValue;
				return LuaDouble.valueOf(n.doubleValue());
			}
		};
		Coercion stringCoercion = new Coercion()
		{
			@Override
			public LuaValue coerce(Object javaValue)
			{
				return LuaString.valueOf(javaValue.toString());
			}
		};
		COERCIONS.put(Boolean.class, boolCoercion);
		COERCIONS.put(Byte.class, intCoercion);
		COERCIONS.put(Character.class, charCoercion);
		COERCIONS.put(Short.class, intCoercion);
		COERCIONS.put(Integer.class, intCoercion);
		COERCIONS.put(Long.class, doubleCoercion);
		COERCIONS.put(Float.class, doubleCoercion);
		COERCIONS.put(Double.class, doubleCoercion);
		COERCIONS.put(String.class, stringCoercion);
	}

	private static final Coercion                              instanceCoercion = new Coercion()
	                                                                            {
		                                                                            @Override
		                                                                            public LuaValue coerce(Object javaValue)
		                                                                            {
			                                                                            return new JavaInstance(javaValue);
		                                                                            }
	                                                                            };

	// should be userdata?
	private static final Coercion                              arrayCoercion    = new Coercion()
	                                                                            {
		                                                                            @Override
		                                                                            public LuaValue coerce(Object javaValue)
		                                                                            {
			                                                                            return new JavaArray(javaValue);
		                                                                            }
	                                                                            };

	/**
	 * Coerse a Java object to a corresponding lua value.
	 * <p>
	 * Integral types {@code boolean}, {@code byte},  {@code char}, and {@code int}
	 * will become {@link LuaInteger};
	 * {@code long}, {@code float}, and {@code double} will become {@link LuaDouble};
	 * {@code String} and {@code byte[]} will become {@link LuaString};
	 * other types will become {@link LuaUserdata}.
	 * @param o Java object needing conversion
	 * @return {@link LuaValue} corresponding to the supplied Java value.
	 * @see LuaValue
	 * @see LuaInteger
	 * @see LuaDouble
	 * @see LuaString
	 * @see LuaUserdata
	 */
	public static LuaValue coerce(Object o)
	{
		if(o == null)
		    return LuaValue.NIL;
		Class<?> clazz = o.getClass();
		Coercion c = COERCIONS.get(clazz);
		if(c == null)
		{
			c = o instanceof Class ? JavaClass.forClass((Class<?>)o) : (clazz.isArray() ? arrayCoercion : instanceCoercion);
			Coercion cc = COERCIONS.putIfAbsent(clazz, c);
			if(cc != null) c = cc;
		}
		return c.coerce(o);
	}
}
