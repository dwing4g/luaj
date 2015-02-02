package org.luaj.vm2.lib;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Abstract base class for Java function implementations that take one argument and
 * return one value.
 * <p>
 * Subclasses need only implement {@link LuaValue#call(LuaValue)} to complete this class,
 * simplifying development.
 * All other uses of {@link #call()}, {@link #invoke(Varargs)},etc,
 * are routed through this method by this class,
 * dropping or extending arguments with {@code nil} values as required.
 * <p>
 * If more than one argument are required, or no arguments are required,
 * or variable argument or variable return values,
 * then use one of the related function
 * {@link LibFunction0}, {@link LibFunction2}, {@link LibFunction3}, or {@link LibFunctionV}.
 * <p>
 * See {@link LibFunction} for more information on implementation libraries and library functions.
 * @see #call(LuaValue)
 * @see LibFunction
 * @see LibFunction0
 * @see LibFunction2
 * @see LibFunction3
 * @see LibFunctionV
 */
abstract public class LibFunction1 extends LibFunction
{
	@Override
	abstract public LuaValue call(LuaValue arg);

	/** Default constructor */
	public LibFunction1()
	{
	}

	/** Constructor with specific environment
	 * @param env The environment to apply during constructon.
	 */
	public LibFunction1(LuaValue env)
	{
		this.env = env;
	}

	@Override
	public final LuaValue call()
	{
		return call(NIL);
	}

	@Override
	public final LuaValue call(LuaValue arg1, LuaValue arg2)
	{
		return call(arg1);
	}

	@Override
	public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3)
	{
		return call(arg1);
	}

	@Override
	public Varargs invoke(Varargs varargs)
	{
		return call(varargs.arg1());
	}
}
