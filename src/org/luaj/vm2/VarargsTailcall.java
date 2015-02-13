package org.luaj.vm2;

/**
 * Subclass of {@link Varargs} that represents a lua tail call
 * in a Java library function execution environment.
 * <p>
 * Since Java doesn't have direct support for tail calls,
 * any lua function whose {@link Prototype} contains the
 * {@link Lua#OP_TAILCALL} bytecode needs a mechanism
 * for tail calls when converting lua-bytecode to java-bytecode.
 * <p>
 * The tail call holds the next function and arguments,
 * and the client a call to {@link #eval()} executes the function
 * repeatedly until the tail calls are completed.
 * <p>
 * Normally, users of luaj need not concern themselves with the
 * details of this mechanism, as it is built into the core
 * execution framework.
 * @see Prototype
 */
public final class VarargsTailcall extends Varargs
{
	private LuaValue _func;
	private Varargs  _args;
	private Varargs  _result;

	public VarargsTailcall(LuaValue f, Varargs args)
	{
		_func = f;
		_args = args;
	}

	public VarargsTailcall(LuaValue object, LuaValue methodname, Varargs args)
	{
		_func = object.get(methodname);
		_args = LuaValue.varargsOf(object, args);
	}

	@Override
	public boolean isTailcall()
	{
		return true;
	}

	@Override
	public Varargs eval()
	{
		while(_result == null)
		{
			Varargs r = _func.onInvoke(_args);
			if(r.isTailcall())
			{
				VarargsTailcall t = (VarargsTailcall)r;
				_func = t._func;
				_args = t._args;
			}
			else
			{
				_result = r;
				_func = null;
				_args = null;
			}
		}
		return _result;
	}

	@Override
	public LuaValue arg(int i)
	{
		if(_result == null)
		    eval();
		return _result.arg(i);
	}

	@Override
	public LuaValue arg1()
	{
		if(_result == null)
		    eval();
		return _result.arg1();
	}

	@Override
	public int narg()
	{
		if(_result == null)
		    eval();
		return _result.narg();
	}
}
