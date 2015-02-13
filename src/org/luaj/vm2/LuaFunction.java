package org.luaj.vm2;

/**
 * Base class for functions implemented in Java.
 * <p>
 * Direct subclass include LibFunction which is the base class for
 * all built-in library functions coded in Java,
 * and {@link LuaClosure}, which represents a lua closure
 * whose bytecode is interpreted when the function is invoked.
 * @see LuaValue
 * @see LuaClosure
 */
public abstract class LuaFunction extends LuaValue
{
	/** Shared static metatable for all functions and closures. */
	public static LuaValue s_metatable;

	protected LuaValue     _env;

	public LuaFunction()
	{
		_env = NIL;
	}

	public LuaFunction(LuaValue env)
	{
		_env = env;
	}

	@Override
	public int type()
	{
		return TFUNCTION;
	}

	@Override
	public String typename()
	{
		return "function";
	}

	@Override
	public boolean isfunction()
	{
		return true;
	}

	@Override
	public LuaValue checkfunction()
	{
		return this;
	}

	@Override
	public LuaFunction optfunction(LuaFunction defval)
	{
		return this;
	}

	@Override
	public LuaValue getmetatable()
	{
		return s_metatable;
	}

	@Override
	public LuaValue getfenv()
	{
		return _env;
	}

	@Override
	public void setfenv(LuaValue env)
	{
		_env = env != null ? env : NIL;
	}
}
