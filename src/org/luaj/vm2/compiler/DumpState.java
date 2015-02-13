package org.luaj.vm2.compiler;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.luaj.vm2.LocVars;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;

public final class DumpState
{
	/** mark for precompiled code (`<esc>Lua') */
	public static final String  LUA_SIGNATURE                   = "\033Lua";

	/** for header of binary files -- this is Lua 5.1 */
	public static final int     LUAC_VERSION                    = 0x51;

	/** for header of binary files -- this is the official format */
	public static final int     LUAC_FORMAT                     = 0;

	/** size of header of binary files */
	public static final int     LUAC_HEADERSIZE                 = 12;

	/** expected lua header bytes */
	private static final byte[] LUAC_HEADER_SIGNATURE           = { '\033', 'L', 'u', 'a' };

	/** set true to allow integer compilation */
	public static final boolean ALLOW_INTEGER_CASTING           = false;

	/** format corresponding to non-number-patched lua, all numbers are floats or doubles */
	public static final int     NUMBER_FORMAT_FLOATS_OR_DOUBLES = 0;

	/** format corresponding to non-number-patched lua, all numbers are ints */
	public static final int     NUMBER_FORMAT_INTS_ONLY         = 1;

	/** format corresponding to number-patched lua, all numbers are 32-bit (4 byte) ints */
	public static final int     NUMBER_FORMAT_NUM_PATCH_INT32   = 4;

	/** default number format */
	public static final int     NUMBER_FORMAT_DEFAULT           = NUMBER_FORMAT_FLOATS_OR_DOUBLES;

	private static final int    SIZEOF_INT                      = 4;
	private static final int    SIZEOF_SIZET                    = 4;
	private static final int    SIZEOF_INSTRUCTION              = 4;

	DataOutputStream            _writer;
	boolean                     _strip;
	int                         _status;

	// header fields
	private int                 NUMBER_FORMAT                   = NUMBER_FORMAT_DEFAULT;
	private int                 SIZEOF_LUA_NUMBER               = 8;
	private boolean             IS_LITTLE_ENDIAN                = false;

	public DumpState(OutputStream w, boolean strip)
	{
		_writer = new DataOutputStream(w);
		_strip = strip;
		_status = 0;
	}

	void dumpBlock(final byte[] b, int size) throws IOException
	{
		_writer.write(b, 0, size);
	}

	void dumpChar(int b) throws IOException
	{
		_writer.write(b);
	}

	void dumpInt(int x) throws IOException
	{
		if(IS_LITTLE_ENDIAN)
		{
			_writer.writeByte(x & 0xff);
			_writer.writeByte((x >> 8) & 0xff);
			_writer.writeByte((x >> 16) & 0xff);
			_writer.writeByte((x >> 24) & 0xff);
		}
		else
			_writer.writeInt(x);
	}

	void dumpString(LuaString s) throws IOException
	{
		final int len = s.len().toint();
		dumpInt(len + 1);
		s.write(_writer, 0, len);
		_writer.write(0);
	}

	void dumpDouble(double d) throws IOException
	{
		long l = Double.doubleToLongBits(d);
		if(IS_LITTLE_ENDIAN)
		{
			dumpInt((int)l);
			dumpInt((int)(l >> 32));
		}
		else
		{
			_writer.writeLong(l);
		}
	}

	void dumpCode(final Prototype f) throws IOException
	{
		final int[] code = f.code;
		int n = code.length;
		dumpInt(n);
		for(int i = 0; i < n; i++)
			dumpInt(code[i]);
	}

	void dumpConstants(final Prototype f) throws IOException
	{
		final LuaValue[] k = f.k;
		int i, n = k.length;
		dumpInt(n);
		for(i = 0; i < n; i++)
		{
			final LuaValue o = k[i];
			switch(o.type())
			{
				case LuaValue.TNIL:
					_writer.write(LuaValue.TNIL);
					break;
				case LuaValue.TBOOLEAN:
					_writer.write(LuaValue.TBOOLEAN);
					dumpChar(o.toboolean() ? 1 : 0);
					break;
				case LuaValue.TNUMBER:
					switch(NUMBER_FORMAT)
					{
						case NUMBER_FORMAT_FLOATS_OR_DOUBLES:
							_writer.write(LuaValue.TNUMBER);
							dumpDouble(o.todouble());
							break;
						case NUMBER_FORMAT_INTS_ONLY:
							if(!ALLOW_INTEGER_CASTING && !o.isint())
							    throw new IllegalArgumentException("not an integer: " + o);
							_writer.write(LuaValue.TNUMBER);
							dumpInt(o.toint());
							break;
						case NUMBER_FORMAT_NUM_PATCH_INT32:
							if(o.isint())
							{
								_writer.write(LuaValue.TINT);
								dumpInt(o.toint());
							}
							else
							{
								_writer.write(LuaValue.TNUMBER);
								dumpDouble(o.todouble());
							}
							break;
						default:
							throw new IllegalArgumentException("number format not supported: " + NUMBER_FORMAT);
					}
					break;
				case LuaValue.TSTRING:
					_writer.write(LuaValue.TSTRING);
					dumpString((LuaString)o);
					break;
				default:
					throw new IllegalArgumentException("bad type for " + o);
			}
		}
		n = f.p.length;
		dumpInt(n);
		for(i = 0; i < n; i++)
			dumpFunction(f.p[i], f.source);
	}

	void dumpDebug(final Prototype f) throws IOException
	{
		int i, n;
		n = (_strip) ? 0 : f.lineinfo.length;
		dumpInt(n);
		for(i = 0; i < n; i++)
			dumpInt(f.lineinfo[i]);
		n = (_strip) ? 0 : f.locvars.length;
		dumpInt(n);
		for(i = 0; i < n; i++)
		{
			LocVars lvi = f.locvars[i];
			dumpString(lvi._varname);
			dumpInt(lvi._startpc);
			dumpInt(lvi._endpc);
		}
		n = (_strip) ? 0 : f.upvalues.length;
		dumpInt(n);
		for(i = 0; i < n; i++)
			dumpString(f.upvalues[i]);
	}

	void dumpFunction(final Prototype f, final LuaString string) throws IOException
	{
		if(f.source == null || f.source.equals(string) || _strip)
			dumpInt(0);
		else
			dumpString(f.source);
		dumpInt(f.linedefined);
		dumpInt(f.lastlinedefined);
		dumpChar(f.nups);
		dumpChar(f.numparams);
		dumpChar(f.is_vararg);
		dumpChar(f.maxstacksize);
		dumpCode(f);
		dumpConstants(f);
		dumpDebug(f);
	}

	void dumpHeader() throws IOException
	{
		_writer.write(LUAC_HEADER_SIGNATURE);
		_writer.write(LUAC_VERSION);
		_writer.write(LUAC_FORMAT);
		_writer.write(IS_LITTLE_ENDIAN ? 1 : 0);
		_writer.write(SIZEOF_INT);
		_writer.write(SIZEOF_SIZET);
		_writer.write(SIZEOF_INSTRUCTION);
		_writer.write(SIZEOF_LUA_NUMBER);
		_writer.write(NUMBER_FORMAT);
	}

	/*
	** dump Lua function as precompiled chunk
	*/
	public static int dump(Prototype f, OutputStream w, boolean strip) throws IOException
	{
		DumpState D = new DumpState(w, strip);
		D.dumpHeader();
		D.dumpFunction(f, null);
		return D._status;
	}

	/**
	 *
	 * @param f the function to dump
	 * @param w the output stream to dump to
	 * @param stripDebug true to strip debugging info, false otherwise
	 * @param numberFormat one of NUMBER_FORMAT_FLOATS_OR_DOUBLES, NUMBER_FORMAT_INTS_ONLY, NUMBER_FORMAT_NUM_PATCH_INT32
	 * @param littleendian true to use little endian for numbers, false for big endian
	 * @return 0 if dump succeeds
	 * @throws IOException
	 * @throws IllegalArgumentException if the number format it not supported
	 */
	public static int dump(Prototype f, OutputStream w, boolean stripDebug, int numberFormat, boolean littleendian) throws IOException
	{
		switch(numberFormat)
		{
			case NUMBER_FORMAT_FLOATS_OR_DOUBLES:
			case NUMBER_FORMAT_INTS_ONLY:
			case NUMBER_FORMAT_NUM_PATCH_INT32:
				break;
			default:
				throw new IllegalArgumentException("number format not supported: " + numberFormat);
		}
		DumpState D = new DumpState(w, stripDebug);
		D.IS_LITTLE_ENDIAN = littleendian;
		D.NUMBER_FORMAT = numberFormat;
		D.SIZEOF_LUA_NUMBER = (numberFormat == NUMBER_FORMAT_INTS_ONLY ? 4 : 8);
		D.dumpHeader();
		D.dumpFunction(f, null);
		return D._status;
	}
}
