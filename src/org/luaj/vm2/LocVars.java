package org.luaj.vm2;

/**
 * Data class to hold debug information relatign to local variables for a {@link Prototype}
 */
public final class LocVars
{
	/** The local variable name */
	public final LuaString _varname;

	/** The instruction offset when the variable comes into scope */
	public int             _startpc;

	/** The instruction offset when the variable goes out of scope */
	public int             _endpc;

	/**
	 * Construct a LocVars instance.
	 * @param varname The local variable name
	 * @param startpc The instruction offset when the variable comes into scope
	 * @param endpc The instruction offset when the variable goes out of scope
	 */
	public LocVars(LuaString varname, int startpc, int endpc)
	{
		_varname = varname;
		_startpc = startpc;
		_endpc = endpc;
	}

	public String tojstring()
	{
		return _varname + " " + _startpc + "-" + _endpc;
	}
}
