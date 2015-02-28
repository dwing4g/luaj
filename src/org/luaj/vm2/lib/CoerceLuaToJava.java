package org.luaj.vm2.lib;

import java.lang.reflect.Array;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Helper class to coerce values from lua to Java within the luajava library.
 * <p>
 * This class is primarily used by the {@link LibLuajava},
 * but can also be used directly when working with Java/lua bindings.
 * <p>
 * To coerce to specific Java values, generally the {@code toType()} methods
 * on {@link LuaValue} may be used:
 * <ul>
 * <li>{@link LuaValue#toboolean()}</li>
 * <li>{@link LuaValue#tobyte()}</li>
 * <li>{@link LuaValue#tochar()}</li>
 * <li>{@link LuaValue#toshort()}</li>
 * <li>{@link LuaValue#toint()}</li>
 * <li>{@link LuaValue#tofloat()}</li>
 * <li>{@link LuaValue#todouble()}</li>
 * <li>{@link LuaValue#tojstring()}</li>
 * <li>{@link LuaValue#touserdata()}</li>
 * <li>{@link LuaValue#touserdata(Class)}</li>
 * </ul>
 * <p>
 * For data in lua tables, the various methods on {@link LuaTable} can be used directly
 * to convert data to something more useful.
 *
 * @see LibLuajava
 * @see CoerceJavaToLua
 */
public final class CoerceLuaToJava
{
	static final int SCORE_NULL_VALUE  = 0x10;
	static final int SCORE_WRONG_TYPE  = 0x100;
	static final int SCORE_UNCOERCIBLE = 0x10000;

	static interface Coercion
	{
		public int score(LuaValue value);

		public Object coerce(LuaValue value);
	}

	/**
	 * Coerce a LuaValue value to a specified java class
	 * @param value LuaValue to coerce
	 * @param clazz Class to coerce into
	 * @return Object of type clazz (or a subclass) with the corresponding value.
	 */
	public static Object coerce(LuaValue value, Class<?> clazz)
	{
		return getCoercion(clazz).coerce(value);
	}

	private static final ConcurrentHashMap<Class<?>, Coercion> COERCIONS = new ConcurrentHashMap<Class<?>, Coercion>();

	private static final class BoolCoercion implements Coercion
	{
		@Override
		public String toString()
		{
			return "BoolCoercion()";
		}

		@Override
		public int score(LuaValue value)
		{
			switch(value.type())
			{
				case LuaValue.TBOOLEAN:
					return 0;
			}
			return 1;
		}

		@Override
		public Object coerce(LuaValue value)
		{
			return value.toboolean() ? Boolean.TRUE : Boolean.FALSE;
		}
	}

	private static final class NumericCoercion implements Coercion
	{
		static final int      TARGET_TYPE_BYTE   = 0;
		static final int      TARGET_TYPE_CHAR   = 1;
		static final int      TARGET_TYPE_SHORT  = 2;
		static final int      TARGET_TYPE_INT    = 3;
		static final int      TARGET_TYPE_LONG   = 4;
		static final int      TARGET_TYPE_FLOAT  = 5;
		static final int      TARGET_TYPE_DOUBLE = 6;
		static final String[] TYPE_NAMES         = { "byte", "char", "short", "int", "long", "float", "double" };
		final int             _targetType;

		@Override
		public String toString()
		{
			return "NumericCoercion(" + TYPE_NAMES[_targetType] + ")";
		}

		NumericCoercion(int targetType)
		{
			_targetType = targetType;
		}

		@Override
		public int score(LuaValue value)
		{
			if(value.isint())
			{
				switch(_targetType)
				{
					case TARGET_TYPE_BYTE:
					{
						int i = value.toint();
						return (i == (byte)i) ? 0 : SCORE_WRONG_TYPE;
					}
					case TARGET_TYPE_CHAR:
					{
						int i = value.toint();
						return (i == (byte)i) ? 1 : (i == (char)i) ? 0 : SCORE_WRONG_TYPE;
					}
					case TARGET_TYPE_SHORT:
					{
						int i = value.toint();
						return (i == (byte)i) ? 1 : (i == (short)i) ? 0 : SCORE_WRONG_TYPE;
					}
					case TARGET_TYPE_INT:
					{
						int i = value.toint();
						return (i == (byte)i) ? 2 : ((i == (char)i) || (i == (short)i)) ? 1 : 0;
					}
					case TARGET_TYPE_FLOAT:
						return 1;
					case TARGET_TYPE_LONG:
						return 1;
					case TARGET_TYPE_DOUBLE:
						return 2;
					default:
						return SCORE_WRONG_TYPE;
				}
			}
			else if(value.isnumber())
			{
				switch(_targetType)
				{
					case TARGET_TYPE_BYTE:
						return SCORE_WRONG_TYPE;
					case TARGET_TYPE_CHAR:
						return SCORE_WRONG_TYPE;
					case TARGET_TYPE_SHORT:
						return SCORE_WRONG_TYPE;
					case TARGET_TYPE_INT:
						return SCORE_WRONG_TYPE;
					case TARGET_TYPE_LONG:
					{
						double d = value.todouble();
						return (d == (long)d) ? 0 : SCORE_WRONG_TYPE;
					}
					case TARGET_TYPE_FLOAT:
					{
						double d = value.todouble();
						return (d == (float)d) ? 0 : SCORE_WRONG_TYPE;
					}
					case TARGET_TYPE_DOUBLE:
					{
						double d = value.todouble();
						return ((d == (long)d) || (d == (float)d)) ? 1 : 0;
					}
					default:
						return SCORE_WRONG_TYPE;
				}
			}
			else
			{
				return SCORE_UNCOERCIBLE;
			}
		}

		@Override
		public Object coerce(LuaValue value)
		{
			switch(_targetType)
			{
				case TARGET_TYPE_BYTE:
					return new Byte((byte)value.toint());
				case TARGET_TYPE_CHAR:
					return new Character((char)value.toint());
				case TARGET_TYPE_SHORT:
					return new Short((short)value.toint());
				case TARGET_TYPE_INT:
					return new Integer(value.toint());
				case TARGET_TYPE_LONG:
					return new Long((long)value.todouble());
				case TARGET_TYPE_FLOAT:
					return new Float((float)value.todouble());
				case TARGET_TYPE_DOUBLE:
					return new Double(value.todouble());
				default:
					return null;
			}
		}
	}

	private static final class StringCoercion implements Coercion
	{
		public static final int TARGET_TYPE_STRING = 0;
		public static final int TARGET_TYPE_BYTES  = 1;
		final int               _targetType;

		public StringCoercion(int targetType)
		{
			_targetType = targetType;
		}

		@Override
		public String toString()
		{
			return "StringCoercion(" + (_targetType == TARGET_TYPE_STRING ? "String" : "byte[]") + ")";
		}

		@Override
		public int score(LuaValue value)
		{
			switch(value.type())
			{
				case LuaValue.TSTRING:
					return value.checkstring().isValidUtf8() ?
					        (_targetType == TARGET_TYPE_STRING ? 0 : 1) :
					        (_targetType == TARGET_TYPE_BYTES ? 0 : SCORE_WRONG_TYPE);
				case LuaValue.TNIL:
					return SCORE_NULL_VALUE;
				default:
					return _targetType == TARGET_TYPE_STRING ? SCORE_WRONG_TYPE : SCORE_UNCOERCIBLE;
			}
		}

		@Override
		public Object coerce(LuaValue value)
		{
			if(value.isnil())
			    return null;
			if(_targetType == TARGET_TYPE_STRING)
			    return value.tojstring();
			LuaString s = value.checkstring();
			byte[] b = new byte[s._length];
			s.copyInto(0, b, 0, b.length);
			return b;
		}
	}

	private static final class ArrayCoercion implements Coercion
	{
		final Class<?> _componentType;
		final Coercion _componentCoercion;

		public ArrayCoercion(Class<?> componentType)
		{
			_componentType = componentType;
			_componentCoercion = getCoercion(componentType);
		}

		@Override
		public String toString()
		{
			return "ArrayCoercion(" + _componentType.getName() + ")";
		}

		@Override
		public int score(LuaValue value)
		{
			switch(value.type())
			{
				case LuaValue.TTABLE:
					return value.length() == 0 ? 0 : _componentCoercion.score(value.get(1));
				case LuaValue.TUSERDATA:
					return inheritanceLevels(_componentType, value.touserdata().getClass().getComponentType());
				case LuaValue.TNIL:
					return SCORE_NULL_VALUE;
				default:
					return SCORE_UNCOERCIBLE;
			}
		}

		@Override
		public Object coerce(LuaValue value)
		{
			switch(value.type())
			{
				case LuaValue.TTABLE:
				{
					int n = value.length();
					Object a = Array.newInstance(_componentType, n);
					for(int i = 0; i < n; i++)
						Array.set(a, i, _componentCoercion.coerce(value.get(i + 1)));
					return a;
				}
				case LuaValue.TUSERDATA:
					return value.touserdata();
				case LuaValue.TNIL:
					return null;
				default:
					return null;
			}

		}
	}

	/**
	 * Determine levels of inheritance between a base class and a subclass
	 * @param baseclass base class to look for
	 * @param subclass class from which to start looking
	 * @return number of inheritance levels between subclass and baseclass,
	 * or SCORE_UNCOERCIBLE if not a subclass
	 */
	private static int inheritanceLevels(Class<?> baseclass, Class<?> subclass)
	{
		if(subclass == null)
		    return SCORE_UNCOERCIBLE;
		if(baseclass == subclass)
		    return 0;
		int min = Math.min(SCORE_UNCOERCIBLE, inheritanceLevels(baseclass, subclass.getSuperclass()) + 1);
		Class<?>[] ifaces = subclass.getInterfaces();
		for(int i = 0; i < ifaces.length; i++)
			min = Math.min(min, inheritanceLevels(baseclass, ifaces[i]) + 1);
		return min;
	}

	private static final class ObjectCoercion implements Coercion
	{
		final Class<?> _targetType;

		ObjectCoercion(Class<?> targetType)
		{
			_targetType = targetType;
		}

		@Override
		public String toString()
		{
			return "ObjectCoercion(" + _targetType.getName() + ")";
		}

		@Override
		public int score(LuaValue value)
		{
			switch(value.type())
			{
				case LuaValue.TNUMBER:
					return inheritanceLevels(_targetType, value.isint() ? Integer.class : Double.class);
				case LuaValue.TBOOLEAN:
					return inheritanceLevels(_targetType, Boolean.class);
				case LuaValue.TSTRING:
					return inheritanceLevels(_targetType, String.class);
				case LuaValue.TUSERDATA:
					return inheritanceLevels(_targetType, value.touserdata().getClass());
				case LuaValue.TNIL:
					return SCORE_NULL_VALUE;
				default:
					return inheritanceLevels(_targetType, value.getClass());
			}
		}

		@Override
		public Object coerce(LuaValue value)
		{
			switch(value.type())
			{
				case LuaValue.TNUMBER:
					return value.isint() ? (Object)new Integer(value.toint()) : (Object)new Double(value.todouble());
				case LuaValue.TBOOLEAN:
					return value.toboolean() ? Boolean.TRUE : Boolean.FALSE;
				case LuaValue.TSTRING:
					return value.tojstring();
				case LuaValue.TUSERDATA:
					return value.optuserdata(_targetType, null);
				case LuaValue.TNIL:
					return null;
				default:
					return value;
			}
		}
	}

	static
	{
		Coercion boolCoercion = new BoolCoercion();
		Coercion byteCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_BYTE);
		Coercion charCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_CHAR);
		Coercion shortCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_SHORT);
		Coercion intCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_INT);
		Coercion longCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_LONG);
		Coercion floatCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_FLOAT);
		Coercion doubleCoercion = new NumericCoercion(NumericCoercion.TARGET_TYPE_DOUBLE);
		Coercion stringCoercion = new StringCoercion(StringCoercion.TARGET_TYPE_STRING);
		Coercion bytesCoercion = new StringCoercion(StringCoercion.TARGET_TYPE_BYTES);

		COERCIONS.put(Boolean.TYPE, boolCoercion);
		COERCIONS.put(Boolean.class, boolCoercion);
		COERCIONS.put(Byte.TYPE, byteCoercion);
		COERCIONS.put(Byte.class, byteCoercion);
		COERCIONS.put(Character.TYPE, charCoercion);
		COERCIONS.put(Character.class, charCoercion);
		COERCIONS.put(Short.TYPE, shortCoercion);
		COERCIONS.put(Short.class, shortCoercion);
		COERCIONS.put(Integer.TYPE, intCoercion);
		COERCIONS.put(Integer.class, intCoercion);
		COERCIONS.put(Long.TYPE, longCoercion);
		COERCIONS.put(Long.class, longCoercion);
		COERCIONS.put(Float.TYPE, floatCoercion);
		COERCIONS.put(Float.class, floatCoercion);
		COERCIONS.put(Double.TYPE, doubleCoercion);
		COERCIONS.put(Double.class, doubleCoercion);
		COERCIONS.put(String.class, stringCoercion);
		COERCIONS.put(byte[].class, bytesCoercion);
	}

	static Coercion getCoercion(Class<?> c)
	{
		Coercion co = COERCIONS.get(c);
		if(co == null)
		{
			co = c.isArray() ? new ArrayCoercion(c.getComponentType()) : new ObjectCoercion(c);
			Coercion cc = COERCIONS.putIfAbsent(c, co);
			if(cc != null) co = cc;
		}
		return co;
	}
}
