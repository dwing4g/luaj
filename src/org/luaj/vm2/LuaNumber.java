package org.luaj.vm2;

/**
 * Base class for representing numbers as lua values directly.
 * <p>
 * The main subclasses are {@link LuaInteger} which holds values that fit in a java int,
 * and {@link LuaDouble} which holds all other number values.
 */
public abstract class LuaNumber extends LuaValue
{
	/** Shared static metatable for all number values represented in lua. */
	public static LuaValue s_metatable;

	@Override
	public int type()
	{
		return TNUMBER;
	}

	@Override
	public String typename()
	{
		return "number";
	}

	@Override
	public LuaNumber checknumber()
	{
		return this;
	}

	@Override
	public LuaNumber checknumber(String errmsg)
	{
		return this;
	}

	@Override
	public LuaNumber optnumber(LuaNumber defval)
	{
		return this;
	}

	@Override
	public LuaValue tonumber()
	{
		return this;
	}

	@Override
	public boolean isnumber()
	{
		return true;
	}

	@Override
	public boolean isstring()
	{
		return true;
	}

	@Override
	public LuaValue getmetatable()
	{
		return s_metatable;
	}

	@Override
	public LuaValue concat(LuaValue rhs)
	{
		return rhs.concatTo(this);
	}

	@Override
	public Buffer concat(Buffer rhs)
	{
		return rhs.concatTo(this);
	}

	@Override
	public LuaValue concatTo(LuaNumber lhs)
	{
		return strvalue().concatTo(lhs.strvalue());
	}

	@Override
	public LuaValue concatTo(LuaString lhs)
	{
		return strvalue().concatTo(lhs);
	}
}
