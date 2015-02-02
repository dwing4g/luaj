package org.luaj.vm2.lib;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * LuaValue that represents a particular public Java constructor.
 * <p>
 * May be called with arguments to return a JavaInstance
 * created by calling the constructor.
 * <p>
 * This class is not used directly.
 * It is returned by calls to {@link JavaClass#new(LuaValue key)}
 * when the value of key is "new".
 * @see CoerceJavaToLua
 * @see CoerceLuaToJava
 */
class JavaConstructor extends JavaMember
{
	private static final Map<Constructor<?>, JavaConstructor> constructors = new ConcurrentHashMap<Constructor<?>, JavaConstructor>();

	static JavaConstructor forConstructor(Constructor<?> c)
	{
		JavaConstructor j = constructors.get(c);
		if(j == null)
		    constructors.put(c, j = new JavaConstructor(c));
		return j;
	}

	public static LuaValue forConstructors(JavaConstructor[] array)
	{
		return new Overload(array);
	}

	final Constructor<?> constructor;

	private JavaConstructor(Constructor<?> c)
	{
		super(c.getParameterTypes(), c.getModifiers());
		this.constructor = c;
	}

	@Override
	public Varargs invoke(Varargs args)
	{
		Object[] a = convertArgs(args);
		try
		{
			return CoerceJavaToLua.coerce(constructor.newInstance(a));
		}
		catch(InvocationTargetException e)
		{
			throw new LuaError(e.getTargetException());
		}
		catch(Exception e)
		{
			return LuaValue.error("coercion error " + e);
		}
	}

	/**
	 * LuaValue that represents an overloaded Java constructor.
	 * <p>
	 * On invocation, will pick the best method from the list, and invoke it.
	 * <p>
	 * This class is not used directly.
	 * It is returned by calls to calls to {@link JavaClass#get(LuaValue key)}
	 * when key is "new" and there is more than one public constructor.
	 */
	static class Overload extends LibFunctionV
	{
		final JavaConstructor[] _constructors;

		public Overload(JavaConstructor[] c)
		{
			this._constructors = c;
		}

		@SuppressWarnings("null")
		@Override
		public Varargs invoke(Varargs args)
		{
			JavaConstructor best = null;
			int score = CoerceLuaToJava.SCORE_UNCOERCIBLE;
			for(int i = 0; i < _constructors.length; i++)
			{
				int s = _constructors[i].score(args);
				if(s < score)
				{
					score = s;
					best = _constructors[i];
					if(score == 0)
					    break;
				}
			}

			// any match?
			if(best == null)
			    LuaValue.error("no coercible public method");

			// invoke it
			return best.invoke(args);
		}
	}
}
