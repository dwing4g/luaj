package org.luaj.vm2;

/**
 * String buffer for use in string library methods, optimized for production
 * of StrValue instances.
 * <p>
 * The buffer can begin initially as a wrapped {@link LuaValue}
 * and only when concatenation actually occurs are the bytes first copied.
 * <p>
 * To convert back to a {@link LuaValue} again,
 * the function {@link Buffer#value()} is used.
 * @see LuaValue
 * @see LuaValue#buffer()
 * @see LuaString
 */
public final class Buffer
{
	/** Default capacity for a buffer: 64 */
	private static final int    DEFAULT_CAPACITY = 64;

	/** Shared static array with no bytes */
	private static final byte[] NOBYTES          = {};

	/** Bytes in this buffer */
	private byte[]              _bytes;

	/** Offset into the byte array */
	private int                 _offset;

	/** Length of this buffer */
	private int                 _length;

	/** Value of this buffer, when not represented in bytes */
	private LuaValue            _value;

	/**
	 * Create buffer with default capacity
	 * @see #DEFAULT_CAPACITY
	 */
	public Buffer()
	{
		this(DEFAULT_CAPACITY);
	}

	/**
	 * Create buffer with specified initial capacity
	 * @param initialCapacity the initial capacity
	 */
	public Buffer(int initialCapacity)
	{
		_bytes = new byte[initialCapacity];
		_offset = 0;
		_length = 0;
		_value = null;
	}

	/**
	 * Create buffer with specified initial value
	 * @param value the initial value
	 */
	public Buffer(LuaValue value)
	{
		_bytes = NOBYTES;
		_offset = _length = 0;
		_value = value;
	}

	/**
	 * Get buffer contents as a {@link LuaValue}
	 * @return value as a {@link LuaValue}, converting as necessary
	 */
	public LuaValue value()
	{
		return _value != null ? _value : tostring();
	}

	/**
	 * Set buffer contents as a {@link LuaValue}
	 * @param value value to set
	 */
	public Buffer setvalue(LuaValue value)
	{
		_bytes = NOBYTES;
		_offset = _length = 0;
		_value = value;
		return this;
	}

	/**
	 * Convert the buffer to a {@link LuaString}
	 * @return the value as a {@link LuaString}
	 */
	public LuaString tostring()
	{
		realloc(_length, 0);
		return LuaString.valueOf(_bytes, _offset, _length);
	}

	/**
	 * Convert the buffer to a Java String
	 * @return the value as a Java String
	 */
	public String tojstring()
	{
		return value().tojstring();
	}

	/**
	 * Convert the buffer to a Java String
	 * @return the value as a Java String
	 */
	@Override
	public String toString()
	{
		return tojstring();
	}

	/**
	 * Append a single byte to the buffer.
	 * @return {@code this} to allow call chaining
	 */
	public Buffer append(byte b)
	{
		makeroom(0, 1);
		_bytes[_offset + _length++] = b;
		return this;
	}

	/**
	 * Append a {@link LuaValue} to the buffer.
	 * @return {@code this} to allow call chaining
	 */
	public Buffer append(LuaValue val)
	{
		append(val.strvalue());
		return this;
	}

	/**
	 * Append a {@link LuaString} to the buffer.
	 * @return {@code this} to allow call chaining
	 */
	public Buffer append(LuaString str)
	{
		final int n = str._length;
		makeroom(0, n);
		str.copyInto(0, _bytes, _offset + _length, n);
		_length += n;
		return this;
	}

	/**
	 * Append a Java String to the buffer.
	 * The Java string will be converted to bytes using the UTF8 encoding.
	 * @return {@code this} to allow call chaining
	 * @see LuaString#encodeToUtf8(char[], byte[], int)
	 */
	public Buffer append(String str)
	{
		char[] chars = str.toCharArray();
		final int n = LuaString.lengthAsUtf8(chars);
		makeroom(0, n);
		LuaString.encodeToUtf8(chars, _bytes, _offset + _length);
		_length += n;
		return this;
	}

	/** Concatenate this buffer onto a {@link LuaValue}
	 * @param lhs the left-hand-side value onto which we are concatenating {@code this}
	 * @return {@link Buffer} for use in call chaining.
	 */
	public Buffer concatTo(LuaValue lhs)
	{
		return setvalue(lhs.concat(value()));
	}

	/** Concatenate this buffer onto a {@link LuaString}
	 * @param lhs the left-hand-side value onto which we are concatenating {@code this}
	 * @return {@link Buffer} for use in call chaining.
	 */
	public Buffer concatTo(LuaString lhs)
	{
		return _value != null && !_value.isstring() ? setvalue(lhs.concat(_value)) : prepend(lhs);
	}

	/** Concatenate this buffer onto a {@link LuaNumber}
	 * <p>
	 * The {@link LuaNumber} will be converted to a string before concatenating.
	 * @param lhs the left-hand-side value onto which we are concatenating {@code this}
	 * @return {@link Buffer} for use in call chaining.
	 */
	public Buffer concatTo(LuaNumber lhs)
	{
		return _value != null && !_value.isstring() ? setvalue(lhs.concat(_value)) : prepend(lhs.strvalue());
	}

	/** Concatenate bytes from a {@link LuaString} onto the front of this buffer
	 * @param s the left-hand-side value which we will concatenate onto the front of {@code this}
	 * @return {@link Buffer} for use in call chaining.
	 */
	public Buffer prepend(LuaString s)
	{
		int n = s._length;
		makeroom(n, 0);
		System.arraycopy(s._bytes, s._offset, _bytes, _offset - n, n);
		_offset -= n;
		_length += n;
		_value = null;
		return this;
	}

	/** Ensure there is enough room before and after the bytes.
	 * @param nbefore number of unused bytes which must precede the data after this completes
	 * @param nafter number of unused bytes which must follow the data after this completes
	 */
	public void makeroom(int nbefore, int nafter)
	{
		if(_value != null)
		{
			LuaString s = _value.strvalue();
			_value = null;
			_length = s._length;
			_offset = nbefore;
			_bytes = new byte[nbefore + _length + nafter];
			System.arraycopy(s._bytes, s._offset, _bytes, _offset, _length);
		}
		else if(_offset + _length + nafter > _bytes.length || _offset < nbefore)
		{
			int n = nbefore + _length + nafter;
			int m = n < 32 ? 32 : n < _length * 2 ? _length * 2 : n;
			realloc(m, nbefore == 0 ? 0 : m - _length - nafter);
		}
	}

	/** Reallocate the internal storage for the buffer
	 * @param newSize the size of the buffer to use
	 * @param newOffset the offset to use
	 */
	private void realloc(int newSize, int newOffset)
	{
		if(newSize != _bytes.length)
		{
			byte[] newBytes = new byte[newSize];
			System.arraycopy(_bytes, _offset, newBytes, newOffset, _length);
			_bytes = newBytes;
			_offset = newOffset;
		}
	}
}
