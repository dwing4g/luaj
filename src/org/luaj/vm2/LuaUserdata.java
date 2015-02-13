package org.luaj.vm2;

public class LuaUserdata extends LuaValue
{
	public final Object _instance;
	public LuaValue     _metatable;

	public LuaUserdata(Object obj)
	{
		_instance = obj;
	}

	public LuaUserdata(Object obj, LuaValue metatable)
	{
		_instance = obj;
		_metatable = metatable;
	}

	@Override
	public String tojstring()
	{
		return String.valueOf(_instance);
	}

	@Override
	public int type()
	{
		return LuaValue.TUSERDATA;
	}

	@Override
	public String typename()
	{
		return "userdata";
	}

	@Override
	public int hashCode()
	{
		return _instance.hashCode();
	}

	public Object userdata()
	{
		return _instance;
	}

	@Override
	public boolean isuserdata()
	{
		return true;
	}

	@Override
	public boolean isuserdata(Class<?> c)
	{
		return c.isAssignableFrom(_instance.getClass());
	}

	@Override
	public Object touserdata()
	{
		return _instance;
	}

	@Override
	public Object touserdata(Class<?> c)
	{
		return c.isAssignableFrom(_instance.getClass()) ? _instance : null;
	}

	@Override
	public Object optuserdata(Object defval)
	{
		return _instance;
	}

	@Override
	public Object optuserdata(Class<?> c, Object defval)
	{
		if(!c.isAssignableFrom(_instance.getClass()))
		    typerror(c.getName());
		return _instance;
	}

	@Override
	public LuaValue getmetatable()
	{
		return _metatable;
	}

	@Override
	public LuaValue setmetatable(LuaValue metatable)
	{
		_metatable = metatable;
		return this;
	}

	@Override
	public Object checkuserdata()
	{
		return _instance;
	}

	@Override
	public Object checkuserdata(Class<?> c)
	{
		if(c.isAssignableFrom(_instance.getClass()))
		    return _instance;
		return typerror(c.getName());
	}

	@Override
	public LuaValue get(LuaValue key)
	{
		return _metatable != null ? gettable(this, key) : NIL;
	}

	@Override
	public void set(LuaValue key, LuaValue value)
	{
		if(_metatable == null || !settable(this, key, value))
		    error("cannot set " + key + " for userdata");
	}

	@Override
	public boolean equals(Object val)
	{
		if(this == val)
		    return true;
		if(!(val instanceof LuaUserdata))
		    return false;
		LuaUserdata u = (LuaUserdata)val;
		return _instance.equals(u._instance);
	}

	// equality w/ metatable processing
	@Override
	public LuaValue eq(LuaValue val)
	{
		return eq_b(val) ? TRUE : FALSE;
	}

	@Override
	public boolean eq_b(LuaValue val)
	{
		if(val.raweq(this)) return true;
		if(_metatable == null || !val.isuserdata()) return false;
		LuaValue valmt = val.getmetatable();
		return valmt != null && LuaValue.eqmtcall(this, _metatable, val, valmt);
	}

	// equality w/o metatable processing
	@Override
	public boolean raweq(LuaValue val)
	{
		return val.raweq(this);
	}

	@Override
	public boolean raweq(LuaUserdata val)
	{
		return this == val || (_metatable == val._metatable && _instance.equals(val._instance));
	}

	// __eq metatag processing
	public boolean eqmt(LuaValue val)
	{
		return _metatable != null && val.isuserdata() ? LuaValue.eqmtcall(this, _metatable, val, val.getmetatable()) : false;
	}
}
