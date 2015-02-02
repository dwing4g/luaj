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
public class VarargsTailcall extends Varargs
{
	private LuaValue func;
	private Varargs  args;
	private Varargs  result;

	public VarargsTailcall(LuaValue f, Varargs args)
	{
		this.func = f;
		this.args = args;
	}

	public VarargsTailcall(LuaValue object, LuaValue methodname, Varargs args)
	{
		this.func = object.get(methodname);
		this.args = LuaValue.varargsOf(object, args);
	}

	@Override
	public boolean isTailcall()
	{
		return true;
	}

	@Override
	public Varargs eval()
	{
		while(result == null)
		{
			Varargs r = func.onInvoke(args);
			if(r.isTailcall())
			{
				VarargsTailcall t = (VarargsTailcall)r;
				func = t.func;
				args = t.args;
			}
			else
			{
				result = r;
				func = null;
				args = null;
			}
		}
		return result;
	}

	@Override
	public LuaValue arg(int i)
	{
		if(result == null)
		    eval();
		return result.arg(i);
	}

	@Override
	public LuaValue arg1()
	{
		if(result == null)
		    eval();
		return result.arg1();
	}

	@Override
	public int narg()
	{
		if(result == null)
		    eval();
		return result.narg();
	}
}
