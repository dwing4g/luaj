package org.luaj.vm2.lib;

import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Abstract base class for Java function implementations that takes varaiable arguments and
 * returns multiple return values.
 * <p>
 * Subclasses need only implement {@link LuaValue#invoke(Varargs)} to complete this class,
 * simplifying development.
 * All other uses of {@link #call(LuaValue)}, {@link #invoke()},etc,
 * are routed through this method by this class,
 * converting arguments to {@linnk Varargs} and
 * dropping or extending return values with {@code nil} values as required.
 * <p>
 * If between one and three arguments are required, and only one return value is returned,
 * {@link LibFunction0}, {@link LibFunction1}, {@link LibFunction2}, or {@link LibFunction3}.
 * <p>
 * See {@link LibFunction} for more information on implementation libraries and library functions.
 * @see #invoke(Varargs)
 * @see LibFunction
 * @see LibFunction0
 * @see LibFunction1
 * @see LibFunction2
 * @see LibFunction3
 */
abstract public class LibFunctionV extends LibFunction
{
	public LibFunctionV()
	{
	}

	public LibFunctionV(LuaValue env)
	{
		this.env = env;
	}

	@Override
	public LuaValue call()
	{
		return invoke(NONE).arg1();
	}

	@Override
	public LuaValue call(LuaValue arg)
	{
		return invoke(arg).arg1();
	}

	@Override
	public LuaValue call(LuaValue arg1, LuaValue arg2)
	{
		return invoke(varargsOf(arg1, arg2)).arg1();
	}

	@Override
	public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3)
	{
		return invoke(varargsOf(arg1, arg2, arg3)).arg1();
	}

	/**
	 * Override and implement for the best performance.
	 * May not have expected behavior for tail calls.
	 * Should not be used if either:
	 * - function needs to be used as a module
	 * - function has a possibility of returning a TailcallVarargs
	 * @param args the arguments to the function call.
	 */
	@Override
	public Varargs invoke(Varargs args)
	{
		LuaThread.CallStack cs = LuaThread.onCall(this);
		try
		{
			return this.onInvoke(args).eval();
		}
		finally
		{
			cs.onReturn();
		}
	}

	/**
	 * Override to provide a call implementation that runs in an environment
	 * that can participate in setfenv, and behaves as expected
	 * when returning TailcallVarargs.
	 * @param args the arguments to the function call.
	 */
	@Override
	public Varargs onInvoke(Varargs args)
	{
		return invoke(args);
	}
}
