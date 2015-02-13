package org.luaj.vm2;

/** Upvalue used with Closure formulation
 * <p>
 * @see LuaClosure
 * @see Prototype
 */
public final class UpValue
{
	LuaValue[] _array; // initially the stack, becomes a holder
	int        _index;

	/**
	 *  Create an upvalue relative to a stack
	 * @param stack the stack
	 * @param index the index on the stack for the upvalue
	 */
	public UpValue(LuaValue[] stack, int index)
	{
		_array = stack;
		_index = index;
	}

	/**
	 * Convert this upvalue to a Java String
	 * @return the Java String for this upvalue.
	 * @see LuaValue#tojstring()
	 */
	public String tojstring()
	{
		return _array[_index].tojstring();
	}

	/**
	 * Get the value of the upvalue
	 * @return the {@link LuaValue} for this upvalue
	 */
	public LuaValue getValue()
	{
		return _array[_index];
	}

	/**
	 * Set the value of the upvalue
	 * @param the {@link LuaValue} to set it to
	 */
	public void setValue(LuaValue value)
	{
		_array[_index] = value;
	}

	/**
	 * Close this upvalue so it is no longer on the stack
	 */
	public void close()
	{
		_array = new LuaValue[] { _array[_index] };
		_index = 0;
	}
}
