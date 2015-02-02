package org.luaj.vm2;

/**
 * Class to encapsulate varargs values, either as part of a variable argument list, or multiple return values.
 * <p>
 * To construct varargs, use one of the static methods such as
 * {@code LuaValue.varargsOf(LuaValue,LuaValue)}
 * <p>
 * Any LuaValue can be used as a stand-in for Varargs, for both calls and return values.
 * When doing so, nargs() will return 1 and arg1() or arg(1) will return this.
 * This simplifies the case when calling or implementing varargs functions with only
 * 1 argument or 1 return value.
 * <p>
 * Varargs can also be derived from other varargs by appending to the front with a call
 * such as  {@code LuaValue.varargsOf(LuaValue,Varargs)}
 * or by taking a portion of the args using {@code Varargs.subargs(int start)}
 * <p>
 * @see LuaValue#varargsOf(LuaValue[])
 * @see LuaValue#varargsOf(LuaValue, Varargs)
 * @see LuaValue#varargsOf(LuaValue[], Varargs)
 * @see LuaValue#varargsOf(LuaValue, LuaValue, Varargs)
 * @see LuaValue#varargsOf(LuaValue[], int, int)
 * @see LuaValue#varargsOf(LuaValue[], int, int, Varargs)
 * @see LuaValue#subargs(int)
 */
public abstract class Varargs
{
	/**
	 * Get the number of arguments, or 0 if there are none.
	 * @return number of arguments.
	 */
	public abstract int narg();

	/**
	 * Get the first argument in the list.
	 * @return LuaValue which is first in the list, or LuaValue.NIL if there are no values.
	 * @see Varargs#arg(int)
	 * @see LuaValue#NIL
	 */
	public abstract LuaValue arg1();

	/**
	 * Get the n-th argument value (1-based).
	 * @param i the index of the argument to get, 1 is the first argument
	 * @return Value at position i, or LuaValue.NIL if there is none.
	 * @see Varargs#arg1()
	 * @see LuaValue#NIL
	 */
	public abstract LuaValue arg(int i);

	/**
	 * Evaluate any pending tail call and return result.
	 * @return the evaluated tail call result
	 */
	public Varargs eval()
	{
		return this;
	}

	/**
	 * Return true if this is a TailcallVarargs
	 * @return true if a tail call, false otherwise
	 */
	@SuppressWarnings("static-method")
	public boolean isTailcall()
	{
		return false;
	}

	// -----------------------------------------------------------------------
	// utilities to get specific arguments and type-check them.
	// -----------------------------------------------------------------------

	/** Tests if argument i is a thread.
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return true if the argument exists and is a lua thread, false otherwise
	 * @see LuaValue.TTHREAD
	 * */
	public boolean isthread(int i)
	{
		return arg(i).isthread();
	}

	/** Tests if a value exists at argument i.
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return true if the argument exists, false otherwise
	 * */
	public boolean isvalue(int i)
	{
		return i > 0 && i <= narg();
	}

	/** Return argument i as a java int value, discarding any fractional part, {@code defval} if nil, or throw a LuaError  if not a number.
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return int value with fraction discarded and truncated if necessary if argument i is number, or defval if not supplied or nil
	 * @exception LuaError if the argument is not a number
	 * */
	public int optint(int i, int defval)
	{
		return arg(i).optint(defval);
	}

	/** Return argument i as a java String if a string or number, {@code defval} if nil, or throw a LuaError  if any other type
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return String value if argument i is a string or number, or defval if not supplied or nil
	 * @exception LuaError if the argument is not a string or number
	 * */
	public String optjstring(int i, String defval)
	{
		return arg(i).optjstring(defval);
	}

	/** Return argument i as a double, or throw an error if it cannot be converted to one.
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return java double value if argument i is a number or string that converts to a number
	 * @exception LuaError if the argument is not a number
	 * */
	public double checkdouble(int i)
	{
		return arg(i).checknumber().todouble();
	}

	/** Return argument i as a function, or throw an error if an incompatible type.
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return LuaValue that can be called if argument i is lua function or closure
	 * @exception LuaError if the argument is not a lua function or closure
	 * */
	public LuaValue checkfunction(int i)
	{
		return arg(i).checkfunction();
	}

	/** Return argument i as a java int value, discarding any fractional part, or throw an error if not a number.
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return int value with fraction discarded and truncated if necessary if argument i is number
	 * @exception LuaError if the argument is not a number
	 * */
	public int checkint(int i)
	{
		return arg(i).checknumber().toint();
	}

	/** Return argument i as a java long value, discarding any fractional part, or throw an error if not a number.
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return long value with fraction discarded and truncated if necessary if argument i is number
	 * @exception LuaError if the argument is not a number
	 * */
	public long checklong(int i)
	{
		return arg(i).checknumber().tolong();
	}

	/** Return argument i as a java String if a string or number, or throw an error if any other type
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return String value if argument i is a string or number
	 * @exception LuaError if the argument is not a string or number
	 * */
	public String checkjstring(int i)
	{
		return arg(i).checkjstring();
	}

	/** Return argument i as a LuaString if a string or number, or throw an error if any other type
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return LuaString value if argument i is a string or number
	 * @exception LuaError if the argument is not a string or number
	 * */
	public LuaString checkstring(int i)
	{
		return arg(i).checkstring();
	}

	/** Return argument i as a LuaTable if a lua table, or throw an error if any other type.
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return LuaTable value if a table
	 * @exception LuaError if the argument is not a lua table
	 * */
	public LuaTable checktable(int i)
	{
		return arg(i).checktable();
	}

	/** Return argument i as a LuaThread if a lua thread, or throw an error if any other type.
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return LuaThread value if a thread
	 * @exception LuaError if the argument is not a lua thread
	 * */
	public LuaThread checkthread(int i)
	{
		return arg(i).checkthread();
	}

	/** Return argument i as a java Object if a userdata, or throw an error if any other type.
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return java Object value if argument i is a userdata
	 * @exception LuaError if the argument is not a userdata
	 * */
	public Object checkuserdata(int i)
	{
		return arg(i).checkuserdata();
	}

	/** Return argument i as a LuaValue if it exists, or throw an error.
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return LuaValue value if the argument exists
	 * @exception LuaError if the argument does not exist.
	 * */
	public LuaValue checkvalue(int i)
	{
		return i <= narg() ? arg(i) : LuaValue.argerror(i, "value expected");
	}

	/** Return argument i as a LuaValue when a user-supplied assertion passes, or throw an error.
	 * @param test user supplied assertion to test against
	 * @param i the index to report in any error message
	 * @param msg the error message to use when the test fails
	 * @return LuaValue value if the value of {@code test} is {@code true}
	 * @exception LuaError if the the value of {@code test} is {@code false}
	 * */
	public static void argcheck(boolean test, int i, String msg)
	{
		if(!test) LuaValue.argerror(i, msg);
	}

	/** Return true if there is no argument or nil at argument i.
	 * @param i the index of the argument to test, 1 is the first argument
	 * @return true if argument i contains either no argument or nil
	 * */
	public boolean isnoneornil(int i)
	{
		return i > narg() || arg(i).isnil();
	}

	/** Convert the list of varargs values to a human readable java String.
	 * @return String value in human readable form such as {1,2}.
	 */
	public String tojstring()
	{
		Buffer sb = new Buffer();
		sb.append("(");
		for(int i = 1, n = narg(); i <= n; i++)
		{
			if(i > 1) sb.append(",");
			sb.append(arg(i).tojstring());
		}
		sb.append(")");
		return sb.tojstring();
	}

	/** Convert the value or values to a java String using Varargs.tojstring()
	 * @return String value in human readable form.
	 * @see Varargs#tojstring()
	 */
	@Override
	public String toString()
	{
		return tojstring();
	}

	/**
	 * Create a {@code Varargs} instance containing arguments starting at index {@code start}
	 * @param start the index from which to include arguments, where 1 is the first argument.
	 * @return Varargs containing argument { start, start+1,  ... , narg-start-1 }
	 */
	public Varargs subargs(int start)
	{
		int end = narg();
		switch(end - start)
		{
			case 0:
				return arg(start);
			case 1:
				return new LuaValue.VarargsPair(arg(start), arg(end));
		}
		return end < start ? (Varargs)LuaValue.NONE : new VarargsSub(this, start, end);
	}

	/**
	 * Implementation of Varargs for use in the Varargs.subargs() function.
	 * @see Varargs#subargs(int)
	 */
	private static class VarargsSub extends Varargs
	{
		private final Varargs v;
		private final int     start;
		private final int     end;

		public VarargsSub(Varargs varargs, int start, int end)
		{
			this.v = varargs;
			this.start = start;
			this.end = end;
		}

		@Override
		public LuaValue arg(int i)
		{
			i += start - 1;
			return i >= start && i <= end ? v.arg(i) : LuaValue.NIL;
		}

		@Override
		public LuaValue arg1()
		{
			return v.arg(start);
		}

		@Override
		public int narg()
		{
			return end - start + 1;
		}
	}
}
