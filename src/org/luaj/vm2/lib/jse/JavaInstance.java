package org.luaj.vm2.lib.jse;

import java.lang.reflect.Field;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;

/**
 * LuaValue that represents a Java instance.
 * <p>
 * Will respond to get() and set() by returning field values or methods.
 * <p>
 * This class is not used directly.
 * It is returned by calls to {@link CoerceJavaToLua#coerce(Object)}
 * when a subclass of Object is supplied.
 * @see CoerceJavaToLua
 * @see CoerceLuaToJava
 */
class JavaInstance extends LuaUserdata
{
	JavaClass jclass;

	JavaInstance(Object instance)
	{
		super(instance);
	}

	@Override
	public LuaValue get(LuaValue key)
	{
		if(jclass == null)
		    jclass = JavaClass.forClass(m_instance.getClass());
		Field f = jclass.getField(key);
		if(f != null)
		    try
		    {
			    return CoerceJavaToLua.coerce(f.get(m_instance));
		    }
		    catch(Exception e)
		    {
			    throw new LuaError(e);
		    }
		LuaValue m = jclass.getMethod(key);
		if(m != null)
		    return m;
		return super.get(key);
	}

	@Override
	public void set(LuaValue key, LuaValue value)
	{
		if(jclass == null)
		    jclass = JavaClass.forClass(m_instance.getClass());
		Field f = jclass.getField(key);
		if(f != null)
		    try
		    {
			    f.set(m_instance, CoerceLuaToJava.coerce(value, f.getType()));
			    return;
		    }
		    catch(Exception e)
		    {
			    throw new LuaError(e);
		    }
		super.set(key, value);
	}
}
