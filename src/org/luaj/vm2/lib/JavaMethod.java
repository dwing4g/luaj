package org.luaj.vm2.lib;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * LuaValue that represents a Java method.
 * <p>
 * Can be invoked via call(LuaValue...) and related methods.
 * <p>
 * This class is not used directly.
 * It is returned by calls to calls to {@link JavaInstance#get(LuaValue key)}
 * when a method is named.
 * @see CoerceJavaToLua
 * @see CoerceLuaToJava
 */
final class JavaMethod extends JavaMember
{
	private static final Map<Method, JavaMember> methods = new ConcurrentHashMap<Method, JavaMember>();

	static JavaMethod forMethod(Method m)
	{
		JavaMethod j = (JavaMethod)methods.get(m);
		if(j == null)
		    methods.put(m, j = new JavaMethod(m));
		return j;
	}

	static LuaFunction forMethods(JavaMethod[] m)
	{
		return new Overload(m);
	}

	private final Method _method;

	private JavaMethod(Method m)
	{
		super(m.getParameterTypes(), m.getModifiers());
		_method = m;
		try
		{
			if(!m.isAccessible())
			    m.setAccessible(true);
		}
		catch(SecurityException s)
		{
		}
	}

	@Override
	public LuaValue call()
	{
		return error("method cannot be called without instance");
	}

	@Override
	public LuaValue call(LuaValue arg)
	{
		return invokeMethod(arg.checkuserdata(), LuaValue.NONE);
	}

	@Override
	public LuaValue call(LuaValue arg1, LuaValue arg2)
	{
		return invokeMethod(arg1.checkuserdata(), arg2);
	}

	@Override
	public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3)
	{
		return invokeMethod(arg1.checkuserdata(), LuaValue.varargsOf(arg2, arg3));
	}

	@Override
	public Varargs invoke(Varargs args)
	{
		return invokeMethod(args.arg1().checkuserdata(), args.subargs(2));
	}

	LuaValue invokeMethod(Object instance, Varargs args)
	{
		Object[] a = convertArgs(args);
		try
		{
			return CoerceJavaToLua.coerce(_method.invoke(instance, a));
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
	 * LuaValue that represents an overloaded Java method.
	 * <p>
	 * On invocation, will pick the best method from the list, and invoke it.
	 * <p>
	 * This class is not used directly.
	 * It is returned by calls to calls to {@link JavaInstance#get(LuaValue key)}
	 * when an overloaded method is named.
	 */
	private static class Overload extends LuaFunction
	{
		private final JavaMethod[] _methods;

		private Overload(JavaMethod[] methods)
		{
			_methods = methods;
		}

		@Override
		public LuaValue call()
		{
			return error("method cannot be called without instance");
		}

		@Override
		public LuaValue call(LuaValue arg)
		{
			return invokeBestMethod(arg.checkuserdata(), LuaValue.NONE);
		}

		@Override
		public LuaValue call(LuaValue arg1, LuaValue arg2)
		{
			return invokeBestMethod(arg1.checkuserdata(), arg2);
		}

		@Override
		public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3)
		{
			return invokeBestMethod(arg1.checkuserdata(), LuaValue.varargsOf(arg2, arg3));
		}

		@Override
		public Varargs invoke(Varargs args)
		{
			return invokeBestMethod(args.arg1().checkuserdata(), args.subargs(2));
		}

		@SuppressWarnings("null")
		private LuaValue invokeBestMethod(Object instance, Varargs args)
		{
			JavaMethod best = null;
			int score = CoerceLuaToJava.SCORE_UNCOERCIBLE;
			for(int i = 0; i < _methods.length; i++)
			{
				int s = _methods[i].score(args);
				if(s < score)
				{
					score = s;
					best = _methods[i];
					if(score == 0)
					    break;
				}
			}

			// any match?
			if(best == null)
			    LuaValue.error("no coercible public method");

			// invoke it
			return best.invokeMethod(instance, args);
		}
	}
}
