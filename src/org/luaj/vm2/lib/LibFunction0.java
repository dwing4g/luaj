package org.luaj.vm2.lib;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Abstract base class for Java function implementations that take no arguments and
 * return one value.
 * <p>
 * Subclasses need only implement {@link LuaValue#call()} to complete this class,
 * simplifying development.
 * All other uses of {@link #call(LuaValue)}, {@link #invoke(Varargs)},etc,
 * are routed through this method by this class.
 * <p>
 * If one or more arguments are required, or variable argument or variable return values,
 * then use one of the related function
 * {@link LibFunction1}, {@link LibFunction2}, {@link LibFunction3}, or {@link LibFunctionV}.
 * <p>
 * See {@link LibFunction} for more information on implementation libraries and library functions.
 * @see #call()
 * @see LibFunction
 * @see LibFunction1
 * @see LibFunction2
 * @see LibFunction3
 * @see LibFunctionV
 */
public abstract class LibFunction0 extends LibFunction
{
	@Override
	public abstract LuaValue call();

	/** Default constructor */
	public LibFunction0()
	{
	}

	/** Constructor with specific environment
	 * @param env The environment to apply during constructon.
	 */
	public LibFunction0(LuaValue env)
	{
		_env = env;
	}

	@Override
	public LuaValue call(LuaValue arg)
	{
		return call();
	}

	@Override
	public LuaValue call(LuaValue arg1, LuaValue arg2)
	{
		return call();
	}

	@Override
	public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3)
	{
		return call();
	}

	@Override
	public Varargs invoke(Varargs varargs)
	{
		return call();
	}
}
