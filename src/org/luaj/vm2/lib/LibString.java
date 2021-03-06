package org.luaj.vm2.lib;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.luaj.vm2.Buffer;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.DumpState;

/**
 * Subclass of {@link LibFunction} which implements the lua standard {@code string}
 * library.
 *
 * <p>
 * To instantiate and use it directly,
 * link it into your globals table via {@link LuaValue#load(LuaValue)} using code such as:
 * <pre> {@code
 * LuaTable _G = new LuaTable();
 * LuaThread.setGlobals(_G);
 * _G.load(new BaseLib());
 * _G.load(new PackageLib());
 * _G.load(new StringLib());
 * System.out.println( _G.get("string").get("upper").call( LuaValue.valueOf("abcde") ) );
 * } </pre>
 * Doing so will ensure the library is properly initialized
 * and loaded into the globals table.
 * <p>
 * This is a direct port of the corresponding library in C.
 * @see LibFunction
 * @see <a href="http://www.lua.org/manual/5.1/manual.html#5.4">http://www.lua.org/manual/5.1/manual.html#5.4</a>
 */
public final class LibString extends LibFunction1
{
	public static LuaTable instance; // thread-safe for reading

	@Override
	public LuaValue call(LuaValue arg)
	{
		LuaTable t = new LuaTable();
		bind(t, StringLib1.class, new String[] {
		        "dump", "len", "lower", "reverse", "upper", });
		bind(t, StringLibV.class, new String[] {
		        "byte", "char", "find", "format",
		        "gmatch", "gsub", "match", "rep",
		        "sub" });
		_env.set("string", t);
		instance = t;
		if(LuaString.s_metatable == null)
		    LuaString.s_metatable = tableOf(new LuaValue[] { INDEX, t });
		LibPackage.instance.LOADED.set("string", t);
		return t;
	}

	static final class StringLib1 extends LibFunction1
	{
		@Override
		public LuaValue call(LuaValue arg)
		{
			switch(_opcode)
			{
				case 0:
					return dump(arg); // dump (function)
				case 1:
					return LibString.len(arg); // len (function)
				case 2:
					return lower(arg); // lower (function)
				case 3:
					return reverse(arg); // reverse (function)
				case 4:
					return upper(arg); // upper (function)
			}
			return NIL;
		}
	}

	static final class StringLibV extends LibFunctionV
	{
		@Override
		public Varargs invoke(Varargs args)
		{
			switch(_opcode)
			{
				case 0:
					return LibString.byte_(args);
				case 1:
					return LibString.char_(args);
				case 2:
					return LibString.find(args);
				case 3:
					return LibString.format(args);
				case 4:
					return LibString.gmatch(args);
				case 5:
					return LibString.gsub(args);
				case 6:
					return LibString.match(args);
				case 7:
					return LibString.rep(args);
				case 8:
					return LibString.sub(args);
			}
			return NONE;
		}
	}

	/**
	 * string.byte (s [, i [, j]])
	 *
	 * Returns the internal numerical codes of the
	 * characters s[i], s[i+1], ..., s[j]. The default value for i is 1; the
	 * default value for j is i.
	 *
	 * Note that numerical codes are not necessarily portable across platforms.
	 *
	 * @param args the calling args
	 */
	static Varargs byte_(Varargs args)
	{
		LuaString s = args.checkstring(1);
		int l = s._length;
		int posi = posrelat(args.optint(2, 1), l);
		int pose = posrelat(args.optint(3, posi), l);
		int n, i;
		if(posi <= 0) posi = 1;
		if(pose > l) pose = l;
		if(posi > pose) return NONE; /* empty interval; return no values */
		n = pose - posi + 1;
		if(posi + n <= pose) /* overflow? */
		    error("string slice too long");
		LuaValue[] v = new LuaValue[n];
		for(i = 0; i < n; i++)
			v[i] = valueOf(s.luaByte(posi + i - 1));
		return varargsOf(v);
	}

	/**
	 * string.char (...)
	 *
	 * Receives zero or more integers. Returns a string with length equal
	 * to the number of arguments, in which each character has the internal
	 * numerical code equal to its corresponding argument.
	 *
	 * Note that numerical codes are not necessarily portable across platforms.
	 *
	 * @param args the calling VM
	 */
	public static Varargs char_(Varargs args)
	{
		int n = args.narg();
		byte[] bytes = new byte[n];
		for(int i = 0, a = 1; i < n; i++, a++)
		{
			int c = args.checkint(a);
			if(c < 0 || c >= 256) argerror(a, "invalid value");
			bytes[i] = (byte)c;
		}
		return LuaString.valueOf(bytes);
	}

	/**
	 * string.dump (function)
	 *
	 * Returns a string containing a binary representation of the given function,
	 * so that a later loadstring on this string returns a copy of the function.
	 * function must be a Lua function without upvalues.
	 *
	 * TODO: port dumping code as optional add-on
	 */
	static LuaValue dump(LuaValue arg)
	{
		LuaValue f = arg.checkfunction();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try
		{
			DumpState.dump(((LuaClosure)f)._p, baos, true);
			return LuaString.valueOf(baos.toByteArray());
		}
		catch(IOException e)
		{
			return error(e.getMessage());
		}
	}

	/**
	 * string.find (s, pattern [, init [, plain]])
	 *
	 * Looks for the first match of pattern in the string s.
	 * If it finds a match, then find returns the indices of s
	 * where this occurrence starts and ends; otherwise, it returns nil.
	 * A third, optional numerical argument init specifies where to start the search;
	 * its default value is 1 and may be negative. A value of true as a fourth,
	 * optional argument plain turns off the pattern matching facilities,
	 * so the function does a plain "find substring" operation,
	 * with no characters in pattern being considered "magic".
	 * Note that if plain is given, then init must be given as well.
	 *
	 * If the pattern has captures, then in a successful match the captured values
	 * are also returned, after the two indices.
	 */
	static Varargs find(Varargs args)
	{
		return str_find_aux(args, true);
	}

	/**
	 * string.format (formatstring, ...)
	 *
	 * Returns a formatted version of its variable number of arguments following
	 * the description given in its first argument (which must be a string).
	 * The format string follows the same rules as the printf family of standard C functions.
	 * The only differences are that the options/modifiers *, l, L, n, p, and h are not supported
	 * and that there is an extra option, q. The q option formats a string in a form suitable
	 * to be safely read back by the Lua interpreter: the string is written between double quotes,
	 * and all double quotes, newlines, embedded zeros, and backslashes in the string are correctly
	 * escaped when written. For instance, the call
	 *   string.format('%q', 'a string with "quotes" and \n new line')
	 *
	 * will produce the string:
	 *    "a string with \"quotes\" and \
	 *    new line"
	 *
	 * The options c, d, E, e, f, g, G, i, o, u, X, and x all expect a number as argument,
	 * whereas q and s expect a string.
	 *
	 * This function does not accept string values containing embedded zeros,
	 * except as arguments to the q option.
	 */
	static Varargs format(Varargs args)
	{
		LuaString fmt = args.checkstring(1);
		final int n = fmt.length();
		Buffer result = new Buffer(n);
		int arg = 1;
		int c;

		for(int i = 0; i < n;)
		{
			switch(c = fmt.luaByte(i++))
			{
				case '\n':
					result.append("\n");
					break;
				default:
					result.append((byte)c);
					break;
				case L_ESC:
					if(i < n)
					{
						if((c = fmt.luaByte(i)) == L_ESC)
						{
							++i;
							result.append((byte)L_ESC);
						}
						else
						{
							arg++;
							FormatDesc fdsc = new FormatDesc(fmt, i);
							i += fdsc.length;
							switch(fdsc.conversion)
							{
								case 'c':
									FormatDesc.format(result, (byte)args.checkint(arg));
									break;
								case 'i':
								case 'd':
									fdsc.format(result, args.checkint(arg));
									break;
								case 'o':
								case 'u':
								case 'x':
								case 'X':
									fdsc.format(result, args.checklong(arg));
									break;
								case 'e':
								case 'E':
								case 'f':
								case 'g':
								case 'G':
									FormatDesc.format(result, args.checkdouble(arg));
									break;
								case 'q':
									addquoted(result, args.checkstring(arg));
									break;
								case 's':
								{
									LuaString s = args.checkstring(arg);
									if(fdsc.precision == -1 && s.length() >= 100)
										result.append(s);
									else
										FormatDesc.format(result, s);
									break;
								}
								default:
									error("invalid option '%" + (char)fdsc.conversion + "' to 'format'");
									break;
							}
						}
					}
			}
		}

		return result.tostring();
	}

	private static void addquoted(Buffer buf, LuaString s)
	{
		int c;
		buf.append((byte)'"');
		for(int i = 0, n = s.length(); i < n; i++)
		{
			switch(c = s.luaByte(i))
			{
				case '"':
				case '\\':
				case '\n':
					buf.append((byte)'\\');
					buf.append((byte)c);
					break;
				case '\r':
					buf.append("\\r");
					break;
				case '\0':
					buf.append("\\000");
					break;
				default:
					buf.append((byte)c);
					break;
			}
		}
		buf.append((byte)'"');
	}

	static class FormatDesc
	{
		private boolean          leftAdjust;
		private boolean          zeroPad;
		private boolean          explicitPlus;
		private boolean          space;
		private static final int MAX_FLAGS = 5;

		private int              width;
		private int              precision;

		public final int         conversion;
		public final int         length;

		public FormatDesc(LuaString strfrmt, final int start)
		{
			int p = start, n = strfrmt.length();
			int c = 0;

			boolean moreFlags = true;
			while(moreFlags)
			{
				switch(c = ((p < n) ? strfrmt.luaByte(p++) : 0))
				{
					case '-':
						leftAdjust = true;
						break;
					case '+':
						explicitPlus = true;
						break;
					case ' ':
						space = true;
						break;
					case '#':
						break;
					case '0':
						zeroPad = true;
						break;
					default:
						moreFlags = false;
						break;
				}
			}
			if(p - start > MAX_FLAGS)
			    error("invalid format (repeated flags)");

			width = -1;
			if(Character.isDigit((char)c))
			{
				width = c - '0';
				c = ((p < n) ? strfrmt.luaByte(p++) : 0);
				if(Character.isDigit((char)c))
				{
					width = width * 10 + (c - '0');
					c = ((p < n) ? strfrmt.luaByte(p++) : 0);
				}
			}

			precision = -1;
			if(c == '.')
			{
				c = ((p < n) ? strfrmt.luaByte(p++) : 0);
				if(Character.isDigit((char)c))
				{
					precision = c - '0';
					c = ((p < n) ? strfrmt.luaByte(p++) : 0);
					if(Character.isDigit((char)c))
					{
						precision = precision * 10 + (c - '0');
						c = ((p < n) ? strfrmt.luaByte(p++) : 0);
					}
				}
			}

			if(Character.isDigit((char)c))
			    error("invalid format (width or precision too long)");

			zeroPad &= !leftAdjust; // '-' overrides '0'
			conversion = c;
			length = p - start;
		}

		public static void format(Buffer buf, byte c)
		{
			// TODO: not clear that any of width, precision, or flags apply here.
			buf.append(c);
		}

		public void format(Buffer buf, long number)
		{
			String digits;

			if(number == 0 && precision == 0)
			{
				digits = "";
			}
			else
			{
				int radix;
				switch(conversion)
				{
					case 'x':
					case 'X':
						radix = 16;
						break;
					case 'o':
						radix = 8;
						break;
					default:
						radix = 10;
						break;
				}
				digits = Long.toString(number, radix);
				if(conversion == 'X')
				    digits = digits.toUpperCase();
			}

			int minwidth = digits.length();
			int ndigits = minwidth;
			int nzeros;

			if(number < 0)
			{
				ndigits--;
			}
			else if(explicitPlus || space)
			{
				minwidth++;
			}

			if(precision > ndigits)
				nzeros = precision - ndigits;
			else if(precision == -1 && zeroPad && width > minwidth)
				nzeros = width - minwidth;
			else
				nzeros = 0;

			minwidth += nzeros;
			int nspaces = width > minwidth ? width - minwidth : 0;

			if(!leftAdjust)
			    pad(buf, ' ', nspaces);

			if(number < 0)
			{
				if(nzeros > 0)
				{
					buf.append((byte)'-');
					digits = digits.substring(1);
				}
			}
			else if(explicitPlus)
			{
				buf.append((byte)'+');
			}
			else if(space)
			{
				buf.append((byte)' ');
			}

			if(nzeros > 0)
			    pad(buf, '0', nzeros);

			buf.append(digits);

			if(leftAdjust)
			    pad(buf, ' ', nspaces);
		}

		public static void format(Buffer buf, double x)
		{
			// TODO
			buf.append(String.valueOf(x));
		}

		public static void format(Buffer buf, LuaString s)
		{
			int nullindex = s.indexOf((byte)'\0', 0);
			if(nullindex != -1)
			    s = s.substring(0, nullindex);
			buf.append(s);
		}

		public static void pad(Buffer buf, char c, int n)
		{
			byte b = (byte)c;
			while(n-- > 0)
				buf.append(b);
		}
	}

	/**
	 * string.gmatch (s, pattern)
	 *
	 * Returns an iterator function that, each time it is called, returns the next captures
	 * from pattern over string s. If pattern specifies no captures, then the
	 * whole match is produced in each call.
	 *
	 * As an example, the following loop
	 *   s = "hello world from Lua"
	 *   for w in string.gmatch(s, "%a+") do
	 *      print(w)
	 *   end
	 *
	 * will iterate over all the words from string s, printing one per line.
	 * The next example collects all pairs key=value from the given string into a table:
	 *   t = {}
	 *   s = "from=world, to=Lua"
	 *   for k, v in string.gmatch(s, "(%w+)=(%w+)") do
	 *     t[k] = v
	 *   end
	 *
	 * For this function, a '^' at the start of a pattern does not work as an anchor,
	 * as this would prevent the iteration.
	 */
	static Varargs gmatch(Varargs args)
	{
		LuaString src = args.checkstring(1);
		LuaString pat = args.checkstring(2);
		return new GMatchAux(args, src, pat);
	}

	static class GMatchAux extends LibFunctionV
	{
		private final int        _srclen;
		private final MatchState _ms;
		private int              _soffset;

		public GMatchAux(Varargs args, LuaString src, LuaString pat)
		{
			_srclen = src.length();
			_ms = new MatchState(args, src, pat);
			_soffset = 0;
		}

		@Override
		public Varargs invoke(Varargs args)
		{
			for(; _soffset < _srclen; _soffset++)
			{
				_ms.reset();
				int res = _ms.match(_soffset, 0);
				if(res >= 0)
				{
					int soff = _soffset;
					_soffset = res;
					return _ms.push_captures(true, soff, res);
				}
			}
			return NIL;
		}
	}

	/**
	 * string.gsub (s, pattern, repl [, n])
	 * Returns a copy of s in which all (or the first n, if given) occurrences of the
	 * pattern have been replaced by a replacement string specified by repl, which
	 * may be a string, a table, or a function. gsub also returns, as its second value,
	 * the total number of matches that occurred.
	 *
	 * If repl is a string, then its value is used for replacement.
	 * The character % works as an escape character: any sequence in repl of the form %n,
	 * with n between 1 and 9, stands for the value of the n-th captured substring (see below).
	 * The sequence %0 stands for the whole match. The sequence %% stands for a single %.
	 *
	 * If repl is a table, then the table is queried for every match, using the first capture
	 * as the key; if the pattern specifies no captures, then the whole match is used as the key.
	 *
	 * If repl is a function, then this function is called every time a match occurs,
	 * with all captured substrings passed as arguments, in order; if the pattern specifies
	 * no captures, then the whole match is passed as a sole argument.
	 *
	 * If the value returned by the table query or by the function call is a string or a number,
	 * then it is used as the replacement string; otherwise, if it is false or nil,
	 * then there is no replacement (that is, the original match is kept in the string).
	 *
	 * Here are some examples:
	 * 	     x = string.gsub("hello world", "(%w+)", "%1 %1")
	 * 	     --> x="hello hello world world"
	 *
	 *	     x = string.gsub("hello world", "%w+", "%0 %0", 1)
	 *	     --> x="hello hello world"
	 *
	 *	     x = string.gsub("hello world from Lua", "(%w+)%s*(%w+)", "%2 %1")
	 *	     --> x="world hello Lua from"
	 *
	 *	     x = string.gsub("home = $HOME, user = $USER", "%$(%w+)", os.getenv)
	 *	     --> x="home = /home/roberto, user = roberto"
	 *
	 *	     x = string.gsub("4+5 = $return 4+5$", "%$(.-)%$", function (s)
	 *	           return loadstring(s)()
	 *       end)
	 *	     --> x="4+5 = 9"
	 *
	 *	     local t = {name="lua", version="5.1"}
	 *	     x = string.gsub("$name-$version.tar.gz", "%$(%w+)", t)
	 *	     --> x="lua-5.1.tar.gz"
	 */
	static Varargs gsub(Varargs args)
	{
		LuaString src = args.checkstring(1);
		final int srclen = src.length();
		LuaString p = args.checkstring(2);
		LuaValue repl = args.arg(3);
		int max_s = args.optint(4, srclen + 1);
		final boolean anchor = p.length() > 0 && p.charAt(0) == '^';

		Buffer lbuf = new Buffer(srclen);
		MatchState ms = new MatchState(args, src, p);

		int soffset = 0;
		int n = 0;
		while(n < max_s)
		{
			ms.reset();
			int res = ms.match(soffset, anchor ? 1 : 0);
			if(res != -1)
			{
				n++;
				ms.add_value(lbuf, soffset, res, repl);
			}
			if(res != -1 && res > soffset)
				soffset = res;
			else if(soffset < srclen)
				lbuf.append((byte)src.luaByte(soffset++));
			else
				break;
			if(anchor)
			    break;
		}
		lbuf.append(src.substring(soffset, srclen));
		return varargsOf(lbuf.tostring(), valueOf(n));
	}

	/**
	 * string.len (s)
	 *
	 * Receives a string and returns its length. The empty string "" has length 0.
	 * Embedded zeros are counted, so "a\000bc\000" has length 5.
	 */
	static LuaValue len(LuaValue arg)
	{
		return arg.checkstring().len();
	}

	/**
	 * string.lower (s)
	 *
	 * Receives a string and returns a copy of this string with all uppercase letters
	 * changed to lowercase. All other characters are left unchanged.
	 * The definition of what an uppercase letter is depends on the current locale.
	 */
	static LuaValue lower(LuaValue arg)
	{
		return valueOf(arg.checkjstring().toLowerCase());
	}

	/**
	 * string.match (s, pattern [, init])
	 *
	 * Looks for the first match of pattern in the string s. If it finds one,
	 * then match returns the captures from the pattern; otherwise it returns
	 * nil. If pattern specifies no captures, then the whole match is returned.
	 * A third, optional numerical argument init specifies where to start the
	 * search; its default value is 1 and may be negative.
	 */
	static Varargs match(Varargs args)
	{
		return str_find_aux(args, false);
	}

	/**
	 * string.rep (s, n)
	 *
	 * Returns a string that is the concatenation of n copies of the string s.
	 */
	static Varargs rep(Varargs args)
	{
		LuaString s = args.checkstring(1);
		int n = args.checkint(2);
		final byte[] bytes = new byte[s.length() * n];
		int len = s.length();
		for(int offset = 0; offset < bytes.length; offset += len)
		{
			s.copyInto(0, bytes, offset, len);
		}
		return LuaString.valueOf(bytes);
	}

	/**
	 * string.reverse (s)
	 *
	 * Returns a string that is the string s reversed.
	 */
	static LuaValue reverse(LuaValue arg)
	{
		LuaString s = arg.checkstring();
		int n = s.length();
		byte[] b = new byte[n];
		for(int i = 0, j = n - 1; i < n; i++, j--)
			b[j] = (byte)s.luaByte(i);
		return LuaString.valueOf(b);
	}

	/**
	 * string.sub (s, i [, j])
	 *
	 * Returns the substring of s that starts at i and continues until j;
	 * i and j may be negative. If j is absent, then it is assumed to be equal to -1
	 * (which is the same as the string length). In particular, the call
	 *    string.sub(s,1,j)
	 * returns a prefix of s with length j, and
	 *   string.sub(s, -i)
	 * returns a suffix of s with length i.
	 */
	static Varargs sub(Varargs args)
	{
		final LuaString s = args.checkstring(1);
		final int l = s.length();

		int start = posrelat(args.checkint(2), l);
		int end = posrelat(args.optint(3, -1), l);

		if(start < 1)
		    start = 1;
		if(end > l)
		    end = l;

		if(start <= end)
		    return s.substring(start - 1, end);
		return EMPTYSTRING;
	}

	/**
	 * string.upper (s)
	 *
	 * Receives a string and returns a copy of this string with all lowercase letters
	 * changed to uppercase. All other characters are left unchanged.
	 * The definition of what a lowercase letter is depends on the current locale.
	 */
	static LuaValue upper(LuaValue arg)
	{
		return valueOf(arg.checkjstring().toUpperCase());
	}

	/**
	 * This utility method implements both string.find and string.match.
	 */
	static Varargs str_find_aux(Varargs args, boolean find)
	{
		LuaString s = args.checkstring(1);
		LuaString pat = args.checkstring(2);
		int init = args.optint(3, 1);

		if(init > 0)
		{
			init = Math.min(init - 1, s.length());
		}
		else if(init < 0)
		{
			init = Math.max(0, s.length() + init);
		}

		boolean fastMatch = find && (args.arg(4).toboolean() || pat.indexOfAny(SPECIALS) == -1);

		if(fastMatch)
		{
			int result = s.indexOf(pat, init);
			if(result != -1)
			{
				return varargsOf(valueOf(result + 1), valueOf(result + pat.length()));
			}
		}
		else
		{
			MatchState ms = new MatchState(args, s, pat);

			boolean anchor = false;
			int poff = 0;
			if(pat.luaByte(0) == '^')
			{
				anchor = true;
				poff = 1;
			}

			int soff = init;
			do
			{
				int res;
				ms.reset();
				if((res = ms.match(soff, poff)) != -1)
				{
					if(find)
					    return varargsOf(valueOf(soff + 1), valueOf(res), ms.push_captures(false, soff, res));
					return ms.push_captures(true, soff, res);
				}
			}
			while(soff++ < s.length() && !anchor);
		}
		return NIL;
	}

	private static int posrelat(int pos, int len)
	{
		return (pos >= 0) ? pos : len + pos + 1;
	}

	// Pattern matching implementation

	private static final int       L_ESC          = '%';
	private static final LuaString SPECIALS       = valueOf("^$*+?.([%-");
	private static final int       MAX_CAPTURES   = 32;

	private static final int       CAP_UNFINISHED = -1;
	private static final int       CAP_POSITION   = -2;

	private static final byte      MASK_ALPHA     = 0x01;
	private static final byte      MASK_LOWERCASE = 0x02;
	private static final byte      MASK_UPPERCASE = 0x04;
	private static final byte      MASK_DIGIT     = 0x08;
	private static final byte      MASK_PUNCT     = 0x10;
	private static final byte      MASK_SPACE     = 0x20;
	private static final byte      MASK_CONTROL   = 0x40;
	private static final byte      MASK_HEXDIGIT  = (byte)0x80;

	private static final byte[]    CHAR_TABLE;

	static
	{
		CHAR_TABLE = new byte[256];

		for(int i = 0; i < 256; ++i)
		{
			final char c = (char)i;
			CHAR_TABLE[i] = (byte)((Character.isDigit(c) ? MASK_DIGIT : 0) |
			        (Character.isLowerCase(c) ? MASK_LOWERCASE : 0) |
			        (Character.isUpperCase(c) ? MASK_UPPERCASE : 0) |
			        ((c < ' ' || c == 0x7F) ? MASK_CONTROL : 0));
			if((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || (c >= '0' && c <= '9'))
			{
				CHAR_TABLE[i] |= MASK_HEXDIGIT;
			}
			if((c >= '!' && c <= '/') || (c >= ':' && c <= '@'))
			{
				CHAR_TABLE[i] |= MASK_PUNCT;
			}
			if((CHAR_TABLE[i] & (MASK_LOWERCASE | MASK_UPPERCASE)) != 0)
			{
				CHAR_TABLE[i] |= MASK_ALPHA;
			}
		}

		CHAR_TABLE[' '] = MASK_SPACE;
		CHAR_TABLE['\r'] |= MASK_SPACE;
		CHAR_TABLE['\n'] |= MASK_SPACE;
		CHAR_TABLE['\t'] |= MASK_SPACE;
		CHAR_TABLE[0x0C /* '\v' */] |= MASK_SPACE;
		CHAR_TABLE['\f'] |= MASK_SPACE;
	}

	static class MatchState
	{
		final LuaString _s;
		final LuaString _p;
		final Varargs   _args;
		int             _level;
		int[]           _cinit;
		int[]           _clen;

		MatchState(Varargs args, LuaString s, LuaString pattern)
		{
			_s = s;
			_p = pattern;
			_args = args;
			_level = 0;
			_cinit = new int[MAX_CAPTURES];
			_clen = new int[MAX_CAPTURES];
		}

		void reset()
		{
			_level = 0;
		}

		private void add_s(Buffer lbuf, LuaString news, int soff, int e)
		{
			int l = news.length();
			for(int i = 0; i < l; ++i)
			{
				byte b = (byte)news.luaByte(i);
				if(b != L_ESC)
				{
					lbuf.append(b);
				}
				else
				{
					++i; // skip ESC
					b = (byte)news.luaByte(i);
					if(!Character.isDigit((char)b))
					{
						lbuf.append(b);
					}
					else if(b == '0')
					{
						lbuf.append(_s.substring(soff, e));
					}
					else
					{
						lbuf.append(push_onecapture(b - '1', soff, e).strvalue());
					}
				}
			}
		}

		public void add_value(Buffer lbuf, int soffset, int end, LuaValue repl)
		{
			switch(repl.type())
			{
				case LuaValue.TSTRING:
				case LuaValue.TNUMBER:
					add_s(lbuf, repl.strvalue(), soffset, end);
					return;

				case LuaValue.TFUNCTION:
					repl = repl.invoke(push_captures(true, soffset, end)).arg1();
					break;

				case LuaValue.TTABLE:
					// Need to call push_onecapture here for the error checking
					repl = repl.get(push_onecapture(0, soffset, end));
					break;

				default:
					error("bad argument: string/function/table expected");
					return;
			}

			if(!repl.toboolean())
			{
				repl = _s.substring(soffset, end);
			}
			else if(!repl.isstring())
			{
				error("invalid replacement value (a " + repl.typename() + ")");
			}
			lbuf.append(repl.strvalue());
		}

		Varargs push_captures(boolean wholeMatch, int soff, int end)
		{
			int nlevels = (_level == 0 && wholeMatch) ? 1 : _level;
			switch(nlevels)
			{
				case 0:
					return NONE;
				case 1:
					return push_onecapture(0, soff, end);
			}
			LuaValue[] v = new LuaValue[nlevels];
			for(int i = 0; i < nlevels; ++i)
				v[i] = push_onecapture(i, soff, end);
			return varargsOf(v);
		}

		private LuaValue push_onecapture(int i, int soff, int end)
		{
			if(i >= _level)
			{
				if(i == 0)
				    return _s.substring(soff, end);
				return error("invalid capture index");
			}
			int l = _clen[i];
			if(l == CAP_UNFINISHED)
			    return error("unfinished capture");
			if(l == CAP_POSITION)
			    return valueOf(_cinit[i] + 1);
			int begin = _cinit[i];
			return _s.substring(begin, begin + l);
		}

		private int check_capture(int l)
		{
			l -= '1';
			if(l < 0 || l >= _level || _clen[l] == CAP_UNFINISHED)
			{
				error("invalid capture index");
			}
			return l;
		}

		private int capture_to_close()
		{
			int lvl = _level;
			for(lvl--; lvl >= 0; lvl--)
				if(_clen[lvl] == CAP_UNFINISHED)
				    return lvl;
			error("invalid pattern capture");
			return 0;
		}

		int classend(int poffset)
		{
			switch(_p.luaByte(poffset++))
			{
				case L_ESC:
					if(poffset == _p.length())
					{
						error("malformed pattern (ends with %)");
					}
					return poffset + 1;

				case '[':
					if(_p.luaByte(poffset) == '^') poffset++;
					do
					{
						if(poffset == _p.length())
						{
							error("malformed pattern (missing ])");
						}
						if(_p.luaByte(poffset++) == L_ESC && poffset != _p.length())
						    poffset++;
					}
					while(_p.luaByte(poffset) != ']');
					return poffset + 1;
				default:
					return poffset;
			}
		}

		static boolean match_class(int c, int cl)
		{
			final char lcl = Character.toLowerCase((char)cl);
			int cdata = CHAR_TABLE[c];

			boolean res;
			switch(lcl)
			{
				case 'a':
					res = (cdata & MASK_ALPHA) != 0;
					break;
				case 'd':
					res = (cdata & MASK_DIGIT) != 0;
					break;
				case 'l':
					res = (cdata & MASK_LOWERCASE) != 0;
					break;
				case 'u':
					res = (cdata & MASK_UPPERCASE) != 0;
					break;
				case 'c':
					res = (cdata & MASK_CONTROL) != 0;
					break;
				case 'p':
					res = (cdata & MASK_PUNCT) != 0;
					break;
				case 's':
					res = (cdata & MASK_SPACE) != 0;
					break;
				case 'w':
					res = (cdata & (MASK_ALPHA | MASK_DIGIT)) != 0;
					break;
				case 'x':
					res = (cdata & MASK_HEXDIGIT) != 0;
					break;
				case 'z':
					res = (c == 0);
					break;
				default:
					return cl == c;
			}
			return (lcl == cl) ? res : !res;
		}

		boolean matchbracketclass(int c, int poff, int ec)
		{
			boolean sig = true;
			if(_p.luaByte(poff + 1) == '^')
			{
				sig = false;
				poff++;
			}
			while(++poff < ec)
			{
				if(_p.luaByte(poff) == L_ESC)
				{
					poff++;
					if(match_class(c, _p.luaByte(poff)))
					    return sig;
				}
				else if((_p.luaByte(poff + 1) == '-') && (poff + 2 < ec))
				{
					poff += 2;
					if(_p.luaByte(poff - 2) <= c && c <= _p.luaByte(poff))
					    return sig;
				}
				else if(_p.luaByte(poff) == c) return sig;
			}
			return !sig;
		}

		boolean singlematch(int c, int poff, int ep)
		{
			switch(_p.luaByte(poff))
			{
				case '.':
					return true;
				case L_ESC:
					return match_class(c, _p.luaByte(poff + 1));
				case '[':
					return matchbracketclass(c, poff, ep - 1);
				default:
					return _p.luaByte(poff) == c;
			}
		}

		/**
		 * Perform pattern matching. If there is a match, returns offset into s
		 * where match ends, otherwise returns -1.
		 */
		int match(int soffset, int poffset)
		{
			while(true)
			{
				// Check if we are at the end of the pattern -
				// equivalent to the '\0' case in the C version, but our pattern
				// string is not NUL-terminated.
				if(poffset == _p.length())
				    return soffset;
				switch(_p.luaByte(poffset))
				{
					case '(':
						if(++poffset < _p.length() && _p.luaByte(poffset) == ')')
						    return start_capture(soffset, poffset + 1, CAP_POSITION);
						return start_capture(soffset, poffset, CAP_UNFINISHED);
					case ')':
						return end_capture(soffset, poffset + 1);
					case L_ESC:
						if(poffset + 1 == _p.length())
						    error("malformed pattern (ends with '%')");
						switch(_p.luaByte(poffset + 1))
						{
							case 'b':
								soffset = matchbalance(soffset, poffset + 2);
								if(soffset == -1) return -1;
								poffset += 4;
								continue;
							case 'f':
							{
								poffset += 2;
								if(_p.luaByte(poffset) != '[')
								{
									error("Missing [ after %f in pattern");
								}
								int ep = classend(poffset);
								int previous = (soffset == 0) ? -1 : _s.luaByte(soffset - 1);
								if(matchbracketclass(previous, poffset, ep - 1) ||
								        matchbracketclass(_s.luaByte(soffset), poffset, ep - 1))
								    return -1;
								poffset = ep;
								continue;
							}
							default:
							{
								int c = _p.luaByte(poffset + 1);
								if(Character.isDigit((char)c))
								{
									soffset = match_capture(soffset, c);
									if(soffset == -1)
									    return -1;
									return match(soffset, poffset + 2);
								}
							}
						}
						//$FALL-THROUGH$
					case '$':
						if(poffset + 1 == _p.length())
						    return (soffset == _s.length()) ? soffset : -1;
				}
				int ep = classend(poffset);
				boolean m = soffset < _s.length() && singlematch(_s.luaByte(soffset), poffset, ep);
				int pc = (ep < _p.length()) ? _p.luaByte(ep) : '\0';

				switch(pc)
				{
					case '?':
						int res;
						if(m && ((res = match(soffset + 1, ep + 1)) != -1))
						    return res;
						poffset = ep + 1;
						continue;
					case '*':
						return max_expand(soffset, poffset, ep);
					case '+':
						return (m ? max_expand(soffset + 1, poffset, ep) : -1);
					case '-':
						return min_expand(soffset, poffset, ep);
					default:
						if(!m)
						    return -1;
						soffset++;
						poffset = ep;
						continue;
				}
			}
		}

		int max_expand(int soff, int poff, int ep)
		{
			int i = 0;
			while(soff + i < _s.length() &&
			        singlematch(_s.luaByte(soff + i), poff, ep))
				i++;
			while(i >= 0)
			{
				int res = match(soff + i, ep + 1);
				if(res != -1)
				    return res;
				i--;
			}
			return -1;
		}

		int min_expand(int soff, int poff, int ep)
		{
			for(;;)
			{
				int res = match(soff, ep + 1);
				if(res != -1)
					return res;
				else if(soff < _s.length() && singlematch(_s.luaByte(soff), poff, ep))
					soff++;
				else
					return -1;
			}
		}

		int start_capture(int soff, int poff, int what)
		{
			int res;
			int lvl = _level;
			if(lvl >= MAX_CAPTURES)
			    error("too many captures");
			_cinit[lvl] = soff;
			_clen[lvl] = what;
			_level = lvl + 1;
			if((res = match(soff, poff)) == -1)
			    _level--;
			return res;
		}

		int end_capture(int soff, int poff)
		{
			int l = capture_to_close();
			int res;
			_clen[l] = soff - _cinit[l];
			if((res = match(soff, poff)) == -1)
			    _clen[l] = CAP_UNFINISHED;
			return res;
		}

		int match_capture(int soff, int l)
		{
			l = check_capture(l);
			int len = _clen[l];
			if((_s.length() - soff) >= len && LuaString.equals(_s, _cinit[l], _s, soff, len))
			    return soff + len;
			return -1;
		}

		int matchbalance(int soff, int poff)
		{
			final int plen = _p.length();
			if(poff == plen || poff + 1 == plen)
			{
				error("unbalanced pattern");
			}
			if(_s.luaByte(soff) != _p.luaByte(poff))
			    return -1;
			int b = _p.luaByte(poff);
			int e = _p.luaByte(poff + 1);
			int cont = 1;
			while(++soff < _s.length())
			{
				if(_s.luaByte(soff) == e)
				{
					if(--cont == 0) return soff + 1;
				}
				else if(_s.luaByte(soff) == b) cont++;
			}
			return -1;
		}
	}
}
