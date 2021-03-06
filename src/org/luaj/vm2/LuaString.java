package org.luaj.vm2;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.lib.LibMath;
import org.luaj.vm2.lib.LibString;

/**
 * Subclass of {@link LuaValue} for representing lua strings.
 * <p>
 * Because lua string values are more nearly sequences of bytes than
 * sequences of characters or unicode code points, the {@link LuaString}
 * implementation holds the string value in an internal byte array.
 * <p>
 * {@link LuaString} values are generally not mutable once constructed,
 * so multiple {@link LuaString} values can chare a single byte array.
 * <p>
 * Currently {@link LuaString}s are pooled via a centrally managed weak table.
 * To ensure that as many string values as possible take advantage of this,
 * Constructors are not exposed directly.  As with number, booleans, and nil,
 * instance construction should be via {@link LuaValue#valueOf(byte[])} or similar API.
 * <p>
 * When Java Strings are used to initialize {@link LuaString} data, the UTF8 encoding is assumed.
 * The functions
 * {@link LuaString#lengthAsUtf8(char[]),
 * {@link LuaString#encodeToUtf8(char[], byte[], int)}, and
 * {@link LuaString#decodeAsUtf8(byte[], int, int)
 * are used to convert back and forth between UTF8 byte arrays and character arrays.
 */
public final class LuaString extends LuaValue
{
	/** The singleton instance representing lua {@code true} */
	public static LuaValue                                                   s_metatable;

	/** The bytes for the string */
	public final byte[]                                                      _bytes;

	/** The offset into the byte array, 0 means start at the first byte */
	public final int                                                         _offset;

	/** The number of bytes that comprise this string */
	public final int                                                         _length;

	private static final ConcurrentHashMap<String, WeakReference<LuaString>> index_java = new ConcurrentHashMap<String, WeakReference<LuaString>>();

	/**
	 * Get a {@link LuaString} instance whose bytes match
	 * the supplied Java String using the UTF8 encoding.
	 * @param string Java String containing characters to encode as UTF8
	 * @return {@link LuaString} with UTF8 bytes corresponding to the supplied String
	 */
	public static LuaString valueOf(String string)
	{
		WeakReference<LuaString> w = index_java.get(string);
		LuaString s = w != null ? w.get() : null;
		if(s == null)
		{
			char[] c = string.toCharArray();
			byte[] b = new byte[lengthAsUtf8(c)];
			encodeToUtf8(c, b, 0);
			LuaString ss = valueOf(b, 0, b.length);
			do
			{
				if(w != null)
				    index_java.remove(string, w);
				WeakReference<LuaString> ww = new WeakReference<LuaString>(ss);
				w = index_java.putIfAbsent(string, ww);
				if(w == null) w = ww;
				s = w.get();
			}
			while(s == null);
		}
		return s;
	}

	/** Construct a {@link LuaString} around a byte array without copying the contents.
	 * <p>
	 * The array is used directly after this is called, so clients must not change contents.
	 * <p>
	 * @param bytes byte buffer
	 * @param off offset into the byte buffer
	 * @param len length of the byte buffer
	 * @return {@link LuaString} wrapping the byte buffer
	 */
	public static LuaString valueOf(byte[] bytes, int off, int len)
	{
		return new LuaString(bytes, off, len);
	}

	/** Construct a {@link LuaString} using the supplied characters as byte values.
	 * <p>
	 * Only th elow-order 8-bits of each character are used, the remainder is ignored.
	 * <p>
	 * This is most useful for constructing byte sequences that do not conform to UTF8.
	 * @param bytes array of char, whose values are truncated at 8-bits each and put into a byte array.
	 * @return {@link LuaString} wrapping a copy of the byte buffer
	 */
	public static LuaString valueOf(char[] bytes)
	{
		int n = bytes.length;
		byte[] b = new byte[n];
		for(int i = 0; i < n; i++)
			b[i] = (byte)bytes[i];
		return valueOf(b, 0, n);
	}

	/** Construct a {@link LuaString} around a byte array without copying the contents.
	 * <p>
	 * The array is used directly after this is called, so clients must not change contents.
	 * <p>
	 * @param bytes byte buffer
	 * @return {@link LuaString} wrapping the byte buffer
	 */
	public static LuaString valueOf(byte[] bytes)
	{
		return valueOf(bytes, 0, bytes.length);
	}

	/** Construct a {@link LuaString} around a byte array without copying the contents.
	 * <p>
	 * The array is used directly after this is called, so clients must not change contents.
	 * <p>
	 * @param bytes byte buffer
	 * @param offset offset into the byte buffer
	 * @param length length of the byte buffer
	 * @return {@link LuaString} wrapping the byte buffer
	 */
	private LuaString(byte[] bytes, int offset, int length)
	{
		_bytes = bytes;
		_offset = offset;
		_length = length;
	}

	@Override
	public boolean isstring()
	{
		return true;
	}

	@Override
	public LuaValue getmetatable()
	{
		return s_metatable;
	}

	@Override
	public int type()
	{
		return LuaValue.TSTRING;
	}

	@Override
	public String typename()
	{
		return "string";
	}

	@Override
	public String tojstring()
	{
		return decodeAsUtf8(_bytes, _offset, _length);
	}

	// get is delegated to the string library
	@Override
	public LuaValue get(LuaValue key)
	{
		return s_metatable != null ? gettable(this, key) : LibString.instance.get(key);
	}

	// unary operators
	@Override
	public LuaValue neg()
	{
		double d = scannumber(10);
		return Double.isNaN(d) ? super.neg() : valueOf(-d);
	}

	// basic binary arithmetic
	@Override
	public LuaValue add(LuaValue rhs)
	{
		double d = scannumber(10);
		return Double.isNaN(d) ? arithmt(ADD, rhs) : rhs.add(d);
	}

	@Override
	public LuaValue add(double rhs)
	{
		return valueOf(checkarith() + rhs);
	}

	@Override
	public LuaValue add(int rhs)
	{
		return valueOf(checkarith() + rhs);
	}

	@Override
	public LuaValue sub(LuaValue rhs)
	{
		double d = scannumber(10);
		return Double.isNaN(d) ? arithmt(SUB, rhs) : rhs.subFrom(d);
	}

	@Override
	public LuaValue sub(double rhs)
	{
		return valueOf(checkarith() - rhs);
	}

	@Override
	public LuaValue sub(int rhs)
	{
		return valueOf(checkarith() - rhs);
	}

	@Override
	public LuaValue subFrom(double lhs)
	{
		return valueOf(lhs - checkarith());
	}

	@Override
	public LuaValue mul(LuaValue rhs)
	{
		double d = scannumber(10);
		return Double.isNaN(d) ? arithmt(MUL, rhs) : rhs.mul(d);
	}

	@Override
	public LuaValue mul(double rhs)
	{
		return valueOf(checkarith() * rhs);
	}

	@Override
	public LuaValue mul(int rhs)
	{
		return valueOf(checkarith() * rhs);
	}

	@Override
	public LuaValue pow(LuaValue rhs)
	{
		double d = scannumber(10);
		return Double.isNaN(d) ? arithmt(POW, rhs) : rhs.powWith(d);
	}

	@Override
	public LuaValue pow(double rhs)
	{
		return LibMath.dpow(checkarith(), rhs);
	}

	@Override
	public LuaValue pow(int rhs)
	{
		return LibMath.dpow(checkarith(), rhs);
	}

	@Override
	public LuaValue powWith(double lhs)
	{
		return LibMath.dpow(lhs, checkarith());
	}

	@Override
	public LuaValue powWith(int lhs)
	{
		return LibMath.dpow(lhs, checkarith());
	}

	@Override
	public LuaValue div(LuaValue rhs)
	{
		double d = scannumber(10);
		return Double.isNaN(d) ? arithmt(DIV, rhs) : rhs.divInto(d);
	}

	@Override
	public LuaValue div(double rhs)
	{
		return LuaDouble.ddiv(checkarith(), rhs);
	}

	@Override
	public LuaValue div(int rhs)
	{
		return LuaDouble.ddiv(checkarith(), rhs);
	}

	@Override
	public LuaValue divInto(double lhs)
	{
		return LuaDouble.ddiv(lhs, checkarith());
	}

	@Override
	public LuaValue mod(LuaValue rhs)
	{
		double d = scannumber(10);
		return Double.isNaN(d) ? arithmt(MOD, rhs) : rhs.modFrom(d);
	}

	@Override
	public LuaValue mod(double rhs)
	{
		return LuaDouble.dmod(checkarith(), rhs);
	}

	@Override
	public LuaValue mod(int rhs)
	{
		return LuaDouble.dmod(checkarith(), rhs);
	}

	@Override
	public LuaValue modFrom(double lhs)
	{
		return LuaDouble.dmod(lhs, checkarith());
	}

	// relational operators, these only work with other strings
	@Override
	public LuaValue lt(LuaValue rhs)
	{
		return rhs.strcmp(this) > 0 ? LuaValue.TRUE : FALSE;
	}

	@Override
	public boolean lt_b(LuaValue rhs)
	{
		return rhs.strcmp(this) > 0;
	}

	@Override
	public boolean lt_b(int rhs)
	{
		typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public boolean lt_b(double rhs)
	{
		typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public LuaValue lteq(LuaValue rhs)
	{
		return rhs.strcmp(this) >= 0 ? LuaValue.TRUE : FALSE;
	}

	@Override
	public boolean lteq_b(LuaValue rhs)
	{
		return rhs.strcmp(this) >= 0;
	}

	@Override
	public boolean lteq_b(int rhs)
	{
		typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public boolean lteq_b(double rhs)
	{
		typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public LuaValue gt(LuaValue rhs)
	{
		return rhs.strcmp(this) < 0 ? LuaValue.TRUE : FALSE;
	}

	@Override
	public boolean gt_b(LuaValue rhs)
	{
		return rhs.strcmp(this) < 0;
	}

	@Override
	public boolean gt_b(int rhs)
	{
		typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public boolean gt_b(double rhs)
	{
		typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public LuaValue gteq(LuaValue rhs)
	{
		return rhs.strcmp(this) <= 0 ? LuaValue.TRUE : FALSE;
	}

	@Override
	public boolean gteq_b(LuaValue rhs)
	{
		return rhs.strcmp(this) <= 0;
	}

	@Override
	public boolean gteq_b(int rhs)
	{
		typerror("attempt to compare string with number");
		return false;
	}

	@Override
	public boolean gteq_b(double rhs)
	{
		typerror("attempt to compare string with number");
		return false;
	}

	// concatenation
	@Override
	public LuaValue concat(LuaValue rhs)
	{
		return rhs.concatTo(this);
	}

	@Override
	public Buffer concat(Buffer rhs)
	{
		return rhs.concatTo(this);
	}

	@Override
	public LuaValue concatTo(LuaNumber lhs)
	{
		return concatTo(lhs.strvalue());
	}

	@Override
	public LuaValue concatTo(LuaString lhs)
	{
		byte[] b = new byte[lhs._length + _length];
		System.arraycopy(lhs._bytes, lhs._offset, b, 0, lhs._length);
		System.arraycopy(_bytes, _offset, b, lhs._length, _length);
		return new LuaString(b, 0, b.length);
	}

	// string comparison
	@Override
	public int strcmp(LuaValue lhs)
	{
		return -lhs.strcmp(this);
	}

	@Override
	public int strcmp(LuaString rhs)
	{
		for(int i = 0, j = 0; i < _length && j < rhs._length; ++i, ++j)
		{
			if(_bytes[_offset + i] != rhs._bytes[rhs._offset + j])
			    return _bytes[_offset + i] - rhs._bytes[rhs._offset + j];
		}
		return _length - rhs._length;
	}

	/** Check for number in arithmetic, or throw aritherror */
	private double checkarith()
	{
		double d = scannumber(10);
		if(Double.isNaN(d))
		    aritherror();
		return d;
	}

	@Override
	public int checkint()
	{
		return (int)(long)checkdouble();
	}

	@Override
	public LuaInteger checkinteger()
	{
		return valueOf(checkint());
	}

	@Override
	public long checklong()
	{
		return (long)checkdouble();
	}

	@Override
	public double checkdouble()
	{
		double d = scannumber(10);
		if(Double.isNaN(d))
		    argerror("number");
		return d;
	}

	@Override
	public LuaNumber checknumber()
	{
		return valueOf(checkdouble());
	}

	@Override
	public LuaNumber checknumber(String msg)
	{
		double d = scannumber(10);
		if(Double.isNaN(d))
		    error(msg);
		return valueOf(d);
	}

	@Override
	public LuaValue tonumber()
	{
		return tonumber(10);
	}

	@Override
	public boolean isnumber()
	{
		double d = scannumber(10);
		return !Double.isNaN(d);
	}

	@Override
	public boolean isint()
	{
		double d = scannumber(10);
		if(Double.isNaN(d))
		    return false;
		int i = (int)d;
		return i == d;
	}

	@Override
	public boolean islong()
	{
		double d = scannumber(10);
		if(Double.isNaN(d))
		    return false;
		long l = (long)d;
		return l == d;
	}

	@Override
	public byte tobyte()
	{
		return (byte)toint();
	}

	@Override
	public char tochar()
	{
		return (char)toint();
	}

	@Override
	public double todouble()
	{
		double d = scannumber(10);
		return Double.isNaN(d) ? 0 : d;
	}

	@Override
	public float tofloat()
	{
		return (float)todouble();
	}

	@Override
	public int toint()
	{
		return (int)tolong();
	}

	@Override
	public long tolong()
	{
		return (long)todouble();
	}

	@Override
	public short toshort()
	{
		return (short)toint();
	}

	@Override
	public double optdouble(double defval)
	{
		return checknumber().checkdouble();
	}

	@Override
	public int optint(int defval)
	{
		return checknumber().checkint();
	}

	@Override
	public LuaInteger optinteger(LuaInteger defval)
	{
		return checknumber().checkinteger();
	}

	@Override
	public long optlong(long defval)
	{
		return checknumber().checklong();
	}

	@Override
	public LuaNumber optnumber(LuaNumber defval)
	{
		return checknumber().checknumber();
	}

	@Override
	public LuaString optstring(LuaString defval)
	{
		return this;
	}

	@Override
	public LuaValue tostring()
	{
		return this;
	}

	@Override
	public String optjstring(String defval)
	{
		return tojstring();
	}

	@Override
	public LuaString strvalue()
	{
		return this;
	}

	public LuaString substring(int beginIndex, int endIndex)
	{
		return new LuaString(_bytes, _offset + beginIndex, endIndex - beginIndex);
	}

	@Override
	public int hashCode()
	{
		int h = _length; /* seed */
		int step = (_length >> 5) + 1; /* if string is too long, don't hash all its chars */
		for(int l1 = _length; l1 >= step; l1 -= step)
			h = h ^ ((h << 5) + (h >> 2) + (_bytes[_offset + l1 - 1] & 0x0FF));
		return h;
	}

	// object comparison, used in key comparison
	@Override
	public boolean equals(Object o)
	{
		return o instanceof LuaString && raweq((LuaString)o);
	}

	// equality w/ metatable processing
	@Override
	public LuaValue eq(LuaValue val)
	{
		return val.raweq(this) ? TRUE : FALSE;
	}

	@Override
	public boolean eq_b(LuaValue val)
	{
		return val.raweq(this);
	}

	// equality w/o metatable processing
	@Override
	public boolean raweq(LuaValue val)
	{
		return val.raweq(this);
	}

	@Override
	public boolean raweq(LuaString s)
	{
		if(this == s)
		    return true;
		if(s._length != _length)
		    return false;
		if(s._bytes == _bytes && s._offset == _offset)
		    return true;
		if(s.hashCode() != hashCode())
		    return false;
		for(int i = 0; i < _length; i++)
			if(s._bytes[s._offset + i] != _bytes[_offset + i])
			    return false;
		return true;
	}

	public static boolean equals(LuaString a, int i, LuaString b, int j, int n)
	{
		return equals(a._bytes, a._offset + i, b._bytes, b._offset + j, n);
	}

	public static boolean equals(byte[] a, int i, byte[] b, int j, int n)
	{
		if(a.length < i + n || b.length < j + n)
		    return false;
		while(--n >= 0)
			if(a[i++] != b[j++])
			    return false;
		return true;
	}

	public void write(DataOutputStream writer, int i, int len) throws IOException
	{
		writer.write(_bytes, _offset + i, len);
	}

	@Override
	public LuaValue len()
	{
		return LuaInteger.valueOf(_length);
	}

	@Override
	public int length()
	{
		return _length;
	}

	public int luaByte(int index)
	{
		return _bytes[_offset + index] & 0x0FF;
	}

	public int charAt(int index)
	{
		if(index < 0 || index >= _length)
		    throw new IndexOutOfBoundsException();
		return luaByte(index);
	}

	@Override
	public String checkjstring()
	{
		return tojstring();
	}

	@Override
	public LuaString checkstring()
	{
		return this;
	}

	/** Convert value to an input stream.
	 *
	 * @return {@link InputStream} whose data matches the bytes in this {@link LuaString}
	 */
	public InputStream toInputStream()
	{
		return new ByteArrayInputStream(_bytes, _offset, _length);
	}

	/**
	 * Copy the bytes of the string into the given byte array.
	 * @param strOffset offset from which to copy
	 * @param bytes destination byte array
	 * @param arrayOffset offset in destination
	 * @param len number of bytes to copy
	 */
	public void copyInto(int strOffset, byte[] bytes, int arrayOffset, int len)
	{
		System.arraycopy(_bytes, _offset + strOffset, bytes, arrayOffset, len);
	}

	/** Java version of strpbrk - find index of any byte that in an accept string.
	 * @param accept {@link LuaString} containing characters to look for.
	 * @return index of first match in the {@code accept} string, or -1 if not found.
	 */
	public int indexOfAny(LuaString accept)
	{
		final int ilimit = _offset + _length;
		final int jlimit = accept._offset + accept._length;
		for(int i = _offset; i < ilimit; ++i)
		{
			for(int j = accept._offset; j < jlimit; ++j)
			{
				if(_bytes[i] == accept._bytes[j])
				{
					return i - _offset;
				}
			}
		}
		return -1;
	}

	/**
	 * Find the index of a byte starting at a point in this string
	 * @param b the byte to look for
	 * @param start the first index in the string
	 * @return index of first match found, or -1 if not found.
	 */
	public int indexOf(byte b, int start)
	{
		for(int i = 0, j = _offset + start; i < _length; ++i)
		{
			if(_bytes[j++] == b)
			    return i;
		}
		return -1;
	}

	/**
	 * Find the index of a string starting at a point in this string
	 * @param s the string to search for
	 * @param start the first index in the string
	 * @return index of first match found, or -1 if not found.
	 */
	public int indexOf(LuaString s, int start)
	{
		final int slen = s.length();
		final int limit = _offset + _length - slen;
		for(int i = _offset + start; i <= limit; ++i)
		{
			if(equals(_bytes, i, s._bytes, s._offset, slen))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Find the last index of a string in this string
	 * @param s the string to search for
	 * @return index of last match found, or -1 if not found.
	 */
	public int lastIndexOf(LuaString s)
	{
		final int slen = s.length();
		final int limit = _offset + _length - slen;
		for(int i = limit; i >= _offset; --i)
		{
			if(equals(_bytes, i, s._bytes, s._offset, slen))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Convert to Java String interpreting as utf8 characters.
	 *
	 * @param bytes byte array in UTF8 encoding to convert
	 * @param offset starting index in byte array
	 * @param length number of bytes to convert
	 * @return Java String corresponding to the value of bytes interpreted using UTF8
	 * @see #lengthAsUtf8(char[])
	 * @see #encodeToUtf8(char[], byte[], int)
	 * @see #isValidUtf8()
	 */
	public static String decodeAsUtf8(byte[] bytes, int offset, int length)
	{
		int i, j, n, b;
		for(i = offset, j = offset + length, n = 0; i < j; ++n)
		{
			switch(0xE0 & bytes[i++])
			{
				case 0xE0:
					++i;
					//$FALL-THROUGH$
				case 0xC0:
					++i;
			}
		}
		char[] chars = new char[n];
		for(i = offset, j = offset + length, n = 0; i < j;)
		{
			chars[n++] = (char)(
			        ((b = bytes[i++]) >= 0 || i >= j) ? b :
			                (b < -32 || i + 1 >= j) ? (((b & 0x3f) << 6) | (bytes[i++] & 0x3f)) :
			                        (((b & 0xf) << 12) | ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f)));
		}
		return new String(chars);
	}

	/**
	 * Count the number of bytes required to encode the string as UTF-8.
	 * @param chars Array of unicode characters to be encoded as UTF-8
	 * @return count of bytes needed to encode using UTF-8
	 * @see #encodeToUtf8(char[], byte[], int)
	 * @see #decodeAsUtf8(byte[], int, int)
	 * @see #isValidUtf8()
	 */
	public static int lengthAsUtf8(char[] chars)
	{
		int i, b;
		char c;
		for(i = b = chars.length; --i >= 0;)
			if((c = chars[i]) >= 0x80)
			    b += (c >= 0x800) ? 2 : 1;
		return b;
	}

	/**
	 * Encode the given Java string as UTF-8 bytes, writing the result to bytes
	 * starting at offset.
	 * <p>
	 * The string should be measured first with lengthAsUtf8
	 * to make sure the given byte array is large enough.
	 * @param chars Array of unicode characters to be encoded as UTF-8
	 * @param bytes byte array to hold the result
	 * @param off offset into the byte array to start writing
	 * @see #lengthAsUtf8(char[])
	 * @see #decodeAsUtf8(byte[], int, int)
	 * @see #isValidUtf8()
	 */
	public static void encodeToUtf8(char[] chars, byte[] bytes, int off)
	{
		final int n = chars.length;
		char c;
		for(int i = 0, j = off; i < n; i++)
		{
			if((c = chars[i]) < 0x80)
			{
				bytes[j++] = (byte)c;
			}
			else if(c < 0x800)
			{
				bytes[j++] = (byte)(0xC0 | ((c >> 6) & 0x1f));
				bytes[j++] = (byte)(0x80 | (c & 0x3f));
			}
			else
			{
				bytes[j++] = (byte)(0xE0 | ((c >> 12) & 0x0f));
				bytes[j++] = (byte)(0x80 | ((c >> 6) & 0x3f));
				bytes[j++] = (byte)(0x80 | (c & 0x3f));
			}
		}
	}

	/** Check that a byte sequence is valid UTF-8
	 * @return true if it is valid UTF-8, otherwise false
	 * @see #lengthAsUtf8(char[])
	 * @see #encodeToUtf8(char[], byte[], int)
	 * @see #decodeAsUtf8(byte[], int, int)
	 */
	public boolean isValidUtf8()
	{
		int i, j;
		for(i = _offset, j = _offset + _length; i < j;)
		{
			int c = _bytes[i++];
			if(c >= 0) continue;
			if(((c & 0xE0) == 0xC0)
			        && i < j
			        && (_bytes[i++] & 0xC0) == 0x80) continue;
			if(((c & 0xF0) == 0xE0)
			        && i + 1 < j
			        && (_bytes[i++] & 0xC0) == 0x80
			        && (_bytes[i++] & 0xC0) == 0x80) continue;
			return false;
		}
		return true;
	}

	// --------------------- number conversion -----------------------

	/**
	 * convert to a number using a supplied base, or NIL if it can't be converted
	 * @param base the base to use, such as 10
	 * @return IntValue, DoubleValue, or NIL depending on the content of the string.
	 * @see LuaValue#tonumber()
	 */
	public LuaValue tonumber(int base)
	{
		double d = scannumber(base);
		return Double.isNaN(d) ? NIL : valueOf(d);
	}

	/**
	 * Convert to a number in a base, or return Double.NaN if not a number.
	 * @param base the base to use, such as 10
	 * @return double value if conversion is valid, or Double.NaN if not
	 */
	public double scannumber(int base)
	{
		if(base >= 2 && base <= 36)
		{
			int i = _offset, j = _offset + _length;
			while(i < j && _bytes[i] == ' ')
				++i;
			while(i < j && _bytes[j - 1] == ' ')
				--j;
			if(i >= j)
			    return Double.NaN;
			if((base == 10 || base == 16) && (_bytes[i] == '0' && i + 1 < j && (_bytes[i + 1] == 'x' || _bytes[i + 1] == 'X')))
			{
				base = 16;
				i += 2;
			}
			double l = scanlong(base, i, j);
			return Double.isNaN(l) && base == 10 ? scandouble(i, j) : l;
		}

		return Double.NaN;
	}

	/**
	 * Scan and convert a long value, or return Double.NaN if not found.
	 * @param base the base to use, such as 10
	 * @param start the index to start searching from
	 * @param end the first index beyond the search range
	 * @return double value if conversion is valid,
	 * or Double.NaN if not
	 */
	private double scanlong(int base, int start, int end)
	{
		long x = 0;
		boolean neg = (_bytes[start] == '-');
		for(int i = (neg ? start + 1 : start); i < end; i++)
		{
			int digit = _bytes[i] - (base <= 10 || (_bytes[i] >= '0' && _bytes[i] <= '9') ? '0' :
			        _bytes[i] >= 'A' && _bytes[i] <= 'Z' ? ('A' - 10) : ('a' - 10));
			if(digit < 0 || digit >= base)
			    return Double.NaN;
			x = x * base + digit;
		}
		return neg ? -x : x;
	}

	/**
	 * Scan and convert a double value, or return Double.NaN if not a double.
	 * @param start the index to start searching from
	 * @param end the first index beyond the search range
	 * @return double value if conversion is valid,
	 * or Double.NaN if not
	 */
	private double scandouble(int start, int end)
	{
		if(end > start + 64) end = start + 64;
		for(int i = start; i < end; i++)
		{
			switch(_bytes[i])
			{
				case '-':
				case '+':
				case '.':
				case 'e':
				case 'E':
				case '0':
				case '1':
				case '2':
				case '3':
				case '4':
				case '5':
				case '6':
				case '7':
				case '8':
				case '9':
					break;
				default:
					return Double.NaN;
			}
		}
		char[] c = new char[end - start];
		for(int i = start; i < end; i++)
			c[i - start] = (char)_bytes[i];
		try
		{
			return Double.parseDouble(new String(c));
		}
		catch(Exception e)
		{
			return Double.NaN;
		}
	}
}
