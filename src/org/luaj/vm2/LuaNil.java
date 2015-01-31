package org.luaj.vm2;

/**
 * Class to encapsulate behavior of the singleton instance {@code nil}
 * <p>
 * There will be one instance of this class, {@link LuaValue#NIL},
 * per Java virtual machine.
 * However, the {@link Varargs} instance {@link LuaValue#NONE}
 * which is the empty list,
 * is also considered treated as a nil value by default.
 * <p>
 * Although it is possible to test for nil using Java == operator,
 * the recommended approach is to use the method {@link LuaValue#isnil()}
 * instead.  By using that any ambiguities between
 * {@link LuaValue#NIL} and {@link LuaValue#NONE} are avoided.
 * @see LuaValue
 * @see LuaValue#NIL
 */
public class LuaNil extends LuaValue
{
	static final LuaNil    _NIL = new LuaNil();

	public static LuaValue s_metatable;

	LuaNil()
	{
	}

	@Override
	public int type()
	{
		return LuaValue.TNIL;
	}

	@Override
	public String typename()
	{
		return "nil";
	}

	@Override
	public String tojstring()
	{
		return "nil";
	}

	@Override
	public LuaValue not()
	{
		return LuaValue.TRUE;
	}

	@Override
	public boolean toboolean()
	{
		return false;
	}

	@Override
	public boolean isnil()
	{
		return true;
	}

	@Override
	public LuaValue getmetatable()
	{
		return s_metatable;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof LuaNil;
	}

	@Override
	public LuaValue checknotnil()
	{
		return argerror("value");
	}

	@Override
	public LuaValue checkvalidkey()
	{
		return typerror("table index");
	}

	// optional argument conversions - nil alwas falls badk to default value
	@Override
	public boolean optboolean(boolean defval)
	{
		return defval;
	}

	@Override
	public LuaClosure optclosure(LuaClosure defval)
	{
		return defval;
	}

	@Override
	public double optdouble(double defval)
	{
		return defval;
	}

	@Override
	public LuaFunction optfunction(LuaFunction defval)
	{
		return defval;
	}

	@Override
	public int optint(int defval)
	{
		return defval;
	}

	@Override
	public LuaInteger optinteger(LuaInteger defval)
	{
		return defval;
	}

	@Override
	public long optlong(long defval)
	{
		return defval;
	}

	@Override
	public LuaNumber optnumber(LuaNumber defval)
	{
		return defval;
	}

	@Override
	public LuaTable opttable(LuaTable defval)
	{
		return defval;
	}

	@Override
	public LuaThread optthread(LuaThread defval)
	{
		return defval;
	}

	@Override
	public String optjstring(String defval)
	{
		return defval;
	}

	@Override
	public LuaString optstring(LuaString defval)
	{
		return defval;
	}

	@Override
	public Object optuserdata(Object defval)
	{
		return defval;
	}

	@Override
	public Object optuserdata(Class<?> c, Object defval)
	{
		return defval;
	}

	@Override
	public LuaValue optvalue(LuaValue defval)
	{
		return defval;
	}
}
