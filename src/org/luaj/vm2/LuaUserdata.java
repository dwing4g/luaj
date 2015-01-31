package org.luaj.vm2;

public class LuaUserdata extends LuaValue
{
	public final Object m_instance;
	public LuaValue     m_metatable;

	public LuaUserdata(Object obj)
	{
		m_instance = obj;
	}

	public LuaUserdata(Object obj, LuaValue metatable)
	{
		m_instance = obj;
		m_metatable = metatable;
	}

	@Override
	public String tojstring()
	{
		return String.valueOf(m_instance);
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
		return m_instance.hashCode();
	}

	public Object userdata()
	{
		return m_instance;
	}

	@Override
	public boolean isuserdata()
	{
		return true;
	}

	@Override
	public boolean isuserdata(Class<?> c)
	{
		return c.isAssignableFrom(m_instance.getClass());
	}

	@Override
	public Object touserdata()
	{
		return m_instance;
	}

	@Override
	public Object touserdata(Class<?> c)
	{
		return c.isAssignableFrom(m_instance.getClass()) ? m_instance : null;
	}

	@Override
	public Object optuserdata(Object defval)
	{
		return m_instance;
	}

	@Override
	public Object optuserdata(Class<?> c, Object defval)
	{
		if(!c.isAssignableFrom(m_instance.getClass()))
		    typerror(c.getName());
		return m_instance;
	}

	@Override
	public LuaValue getmetatable()
	{
		return m_metatable;
	}

	@Override
	public LuaValue setmetatable(LuaValue metatable)
	{
		this.m_metatable = metatable;
		return this;
	}

	@Override
	public Object checkuserdata()
	{
		return m_instance;
	}

	@Override
	public Object checkuserdata(Class<?> c)
	{
		if(c.isAssignableFrom(m_instance.getClass()))
		    return m_instance;
		return typerror(c.getName());
	}

	@Override
	public LuaValue get(LuaValue key)
	{
		return m_metatable != null ? gettable(this, key) : NIL;
	}

	@Override
	public void set(LuaValue key, LuaValue value)
	{
		if(m_metatable == null || !settable(this, key, value))
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
		return m_instance.equals(u.m_instance);
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
		if(m_metatable == null || !val.isuserdata()) return false;
		LuaValue valmt = val.getmetatable();
		return valmt != null && LuaValue.eqmtcall(this, m_metatable, val, valmt);
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
		return this == val || (m_metatable == val.m_metatable && m_instance.equals(val.m_instance));
	}

	// __eq metatag processing
	public boolean eqmt(LuaValue val)
	{
		return m_metatable != null && val.isuserdata() ? LuaValue.eqmtcall(this, m_metatable, val, val.getmetatable()) : false;
	}
}
