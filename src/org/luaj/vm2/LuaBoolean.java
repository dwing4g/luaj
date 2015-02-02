package org.luaj.vm2;

/**
 * Extension of {@link LuaValue} which can hold a Java boolean as its value.
 * <p>
 * These instance are not instantiated directly by clients.
 * Instead, there are exactly twon instances of this class,
 * {@link LuaValue#TRUE} and {@link LuaValue#FALSE}
 * representing the lua values {@code true} and {@link false}.
 * The function {@link LuaValue#valueOf(boolean)} will always
 * return one of these two values.
 * <p>
 * Any {@link LuaValue} can be converted to its equivalent
 * boolean representation using {@link LuaValue#toboolean()}
 * <p>
 * @see LuaValue
 * @see LuaValue#valueOf(boolean)
 * @see LuaValue#TRUE
 * @see LuaValue#FALSE
 */
public final class LuaBoolean extends LuaValue
{
	/** Shared static metatable for boolean values represented in lua. */
	public static LuaValue s_metatable;

	public final boolean   v;

	LuaBoolean(boolean b)
	{
		v = b;
	}

	@Override
	public int type()
	{
		return LuaValue.TBOOLEAN;
	}

	@Override
	public String typename()
	{
		return "boolean";
	}

	@Override
	public boolean isboolean()
	{
		return true;
	}

	@Override
	public LuaValue not()
	{
		return v ? FALSE : LuaValue.TRUE;
	}

	/**
	 * Return the boolean value for this boolean
	 * @return value as a Java boolean
	 */
	public boolean booleanValue()
	{
		return v;
	}

	@Override
	public boolean toboolean()
	{
		return v;
	}

	@Override
	public String tojstring()
	{
		return v ? "true" : "false";
	}

	@Override
	public boolean optboolean(boolean defval)
	{
		return v;
	}

	@Override
	public boolean checkboolean()
	{
		return v;
	}

	@Override
	public LuaValue getmetatable()
	{
		return s_metatable;
	}
}
