package org.luaj.vm2.lib;

import java.lang.reflect.Array;
import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;

/**
 * LuaValue that represents a Java instance of array type.
 * <p>
 * Can get elements by their integer key index, as well as the length.
 * <p>
 * This class is not used directly.
 * It is returned by calls to {@link CoerceJavaToLua#coerce(Object)}
 * when an array is supplied.
 * @see CoerceJavaToLua
 * @see CoerceLuaToJava
 */
final class JavaArray extends LuaUserdata
{
	private static final LuaValue LENGTH = valueOf("length");

	JavaArray(Object instance)
	{
		super(instance);
	}

	@Override
	public LuaValue get(LuaValue key)
	{
		if(key.equals(LENGTH))
		    return valueOf(Array.getLength(_instance));
		if(key.isint())
		{
			int i = key.toint() - 1;
			return i >= 0 && i < Array.getLength(_instance) ?
			        CoerceJavaToLua.coerce(Array.get(_instance, key.toint() - 1)) : NIL;
		}
		return super.get(key);
	}

	@Override
	public void set(LuaValue key, LuaValue value)
	{
		if(key.isint())
		{
			int i = key.toint() - 1;
			if(i >= 0 && i < Array.getLength(_instance))
				Array.set(_instance, i, CoerceLuaToJava.coerce(value, _instance.getClass().getComponentType()));
			else if(_metatable == null || !settable(this, key, value))
			    error("array index out of bounds");
		}
		else
			super.set(key, value);
	}
}
