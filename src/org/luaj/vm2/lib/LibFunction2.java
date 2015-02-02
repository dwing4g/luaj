package org.luaj.vm2.lib;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Abstract base class for Java function implementations that take two arguments and
 * return one value.
 * <p>
 * Subclasses need only implement {@link LuaValue#call(LuaValue,LuaValue)} to complete this class,
 * simplifying development.
 * All other uses of {@link #call()}, {@link #invoke(Varargs)},etc,
 * are routed through this method by this class,
 * dropping or extending arguments with {@code nil} values as required.
 * <p>
 * If more or less than two arguments are required,
 * or variable argument or variable return values,
 * then use one of the related function
 * {@link LibFunction0}, {@link LibFunction1}, {@link LibFunction3}, or {@link LibFunctionV}.
 * <p>
 * See {@link LibFunction} for more information on implementation libraries and library functions.
 * @see #call(LuaValue,LuaValue)
 * @see LibFunction
 * @see LibFunction0
 * @see LibFunction1
 * @see LibFunction3
 * @see LibFunctionV
 */
abstract public class LibFunction2 extends LibFunction
{
	@Override
	abstract public LuaValue call(LuaValue arg1, LuaValue arg2);

	/** Default constructor */
	public LibFunction2()
	{
	}

	/** Constructor with specific environment
	 * @param env The environment to apply during constructon.
	 */
	public LibFunction2(LuaValue env)
	{
		this.env = env;
	}

	@Override
	public final LuaValue call()
	{
		return call(NIL, NIL);
	}

	@Override
	public final LuaValue call(LuaValue arg)
	{
		return call(arg, NIL);
	}

	@Override
	public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3)
	{
		return call(arg1, arg2);
	}

	@Override
	public Varargs invoke(Varargs varargs)
	{
		return call(varargs.arg1(), varargs.arg(2));
	}
}
