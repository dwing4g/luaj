package org.luaj.vm2.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import org.luaj.vm2.LocVars;
import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.compiler.FuncState.BlockCnt;

public class LexState
{
	protected static final String                 RESERVED_LOCAL_VAR_FOR_CONTROL    = "(for control)";
	protected static final String                 RESERVED_LOCAL_VAR_FOR_STATE      = "(for state)";
	protected static final String                 RESERVED_LOCAL_VAR_FOR_GENERATOR  = "(for generator)";
	protected static final String                 RESERVED_LOCAL_VAR_FOR_STEP       = "(for step)";
	protected static final String                 RESERVED_LOCAL_VAR_FOR_LIMIT      = "(for limit)";
	protected static final String                 RESERVED_LOCAL_VAR_FOR_INDEX      = "(for index)";

	// keywords array
	protected static final String[]               RESERVED_LOCAL_VAR_KEYWORDS       = new String[] {
	                                                                                RESERVED_LOCAL_VAR_FOR_CONTROL,
	                                                                                RESERVED_LOCAL_VAR_FOR_GENERATOR,
	                                                                                RESERVED_LOCAL_VAR_FOR_INDEX,
	                                                                                RESERVED_LOCAL_VAR_FOR_LIMIT,
	                                                                                RESERVED_LOCAL_VAR_FOR_STATE,
	                                                                                RESERVED_LOCAL_VAR_FOR_STEP
	                                                                                };
	private static final HashMap<String, Boolean> RESERVED_LOCAL_VAR_KEYWORDS_TABLE = new HashMap<String, Boolean>();

	static
	{
		for(int i = 0; i < RESERVED_LOCAL_VAR_KEYWORDS.length; i++)
			RESERVED_LOCAL_VAR_KEYWORDS_TABLE.put(RESERVED_LOCAL_VAR_KEYWORDS[i], Boolean.TRUE);
	}

	private static final int                      EOZ                               = -1;
	private static final int                      MAXSRC                            = 80;
	private static final int                      MAX_INT                           = Integer.MAX_VALUE - 2;
	private static final int                      UCHAR_MAX                         = 255;                           // TODO, convert to unicode CHAR_MAX?
	private static final int                      LUAI_MAXCCALLS                    = 200;

	private static String LUA_QS(String s)
	{
		return "'" + s + "'";
	}

	private static String LUA_QL(Object o)
	{
		return LUA_QS(String.valueOf(o));
	}

	private static final boolean LUA_COMPAT_VARARG = true;

	public static boolean isReservedKeyword(String varName)
	{
		return RESERVED_LOCAL_VAR_KEYWORDS_TABLE.containsKey(varName);
	}

	/*
	** Marks the end of a patch list. It is an invalid value both as an absolute
	** address, and as a list link (would link an element to itself).
	*/
	static final int NO_JUMP = -1;

	/*
	** grep "ORDER OPR" if you change these enums
	*/
	static final int OPR_ADD = 0, OPR_SUB = 1, OPR_MUL = 2, OPR_DIV = 3, OPR_MOD = 4, OPR_POW = 5,
	                         OPR_CONCAT = 6,
	                         OPR_NE = 7, OPR_EQ = 8,
	                         OPR_LT = 9, OPR_LE = 10, OPR_GT = 11, OPR_GE = 12,
	                         OPR_AND = 13, OPR_OR = 14,
	                         OPR_NOBINOPR = 15;

	static final int OPR_MINUS = 0, OPR_NOT = 1, OPR_LEN = 2, OPR_NOUNOPR = 3;

	/* exp kind */
	static final int VVOID     = 0, /* no value */
	                           VNIL = 1,
	                           VTRUE = 2,
	                           VFALSE = 3,
	                           VK = 4, /* info = index of constant in `k' */
	                           VKNUM = 5, /* nval = numerical value */
	                           VLOCAL = 6, /* info = local register */
	                           VUPVAL = 7, /* info = index of upvalue in `upvalues' */
	                           VGLOBAL = 8, /* info = index of table, aux = index of global name in `k' */
	                           VINDEXED = 9, /* info = table register, aux = index register (or `k') */
	                           VJMP = 10, /* info = instruction pc */
	                           VRELOCABLE = 11, /* info = instruction pc */
	                           VNONRELOC = 12, /* info = result register */
	                           VCALL = 13, /* info = instruction pc */
	                           VVARARG = 14;                                  /* info = instruction pc */

	/* semantics information */
	private static class SemInfo
	{
		LuaValue  r;
		LuaString ts;
	}

	private static class Token
	{
		int           token;
		final SemInfo seminfo = new SemInfo();

		public void set(Token other)
		{
			token = other.token;
			seminfo.r = other.seminfo.r;
			seminfo.ts = other.seminfo.ts;
		}
	}

	int                                      _current;                    /* current character (charint) */
	int                                      _linenumber;                 /* input line counter */
	int                                      _lastline;                   /* line of last token `consumed' */
	final Token                              _t             = new Token(); /* current token */
	final Token                              _lookahead     = new Token(); /* look ahead token */
	FuncState                                _fs;                         /* `FuncState' is private to the parser */
	LuaC                                     _L;
	InputStream                              _z;                          /* input stream */
	byte[]                                   _buff;                       /* buffer for tokens */
	int                                      _nbuff;                      /* length of buffer */
	LuaString                                _source;                     /* current source name */
	byte                                     _decpoint;                   /* locale decimal point */

	/* ORDER RESERVED */
	static final String                      luaX_tokens[]  = {
	                                                        "and", "break", "do", "else", "elseif",
	                                                        "end", "false", "for", "function", "if",
	                                                        "in", "local", "nil", "not", "or", "repeat",
	                                                        "return", "then", "true", "until", "while",
	                                                        "..", "...", "==", ">=", "<=", "~=",
	                                                        "<number>", "<name>", "<string>", "<eof>",
	                                                        };

	static final int
	                                         /* terminal symbols denoted by reserved words */
	                                         TK_AND         = 257, TK_BREAK = 258, TK_DO = 259, TK_ELSE = 260, TK_ELSEIF = 261,
	                                                        TK_END = 262, TK_FALSE = 263, TK_FOR = 264, TK_FUNCTION = 265, TK_IF = 266,
	                                                        TK_IN = 267, TK_LOCAL = 268, TK_NIL = 269, TK_NOT = 270, TK_OR = 271, TK_REPEAT = 272,
	                                                        TK_RETURN = 273, TK_THEN = 274, TK_TRUE = 275, TK_UNTIL = 276, TK_WHILE = 277,
	                                                        /* other terminal symbols */
	                                                        TK_CONCAT = 278, TK_DOTS = 279, TK_EQ = 280, TK_GE = 281, TK_LE = 282, TK_NE = 283,
	                                                        TK_NUMBER = 284, TK_NAME = 285, TK_STRING = 286, TK_EOS = 287;

	static final int                         FIRST_RESERVED = TK_AND;
	static final int                         NUM_RESERVED   = TK_WHILE + 1 - FIRST_RESERVED;

	static final HashMap<LuaString, Integer> RESERVED       = new HashMap<LuaString, Integer>();

	static
	{
		for(int i = 0; i < NUM_RESERVED; i++)
		{
			LuaString ts = LuaValue.valueOf(luaX_tokens[i]);
			RESERVED.put(ts, new Integer(FIRST_RESERVED + i));
		}
	}

	private static boolean isalnum(int c)
	{
		return c >= '0' && c <= '9'
		        || c >= 'a' && c <= 'z'
		        || c >= 'A' && c <= 'Z'
		        || c == '_';
	}

	private static boolean isalpha(int c)
	{
		return c >= 'a' && c <= 'z'
		        || c >= 'A' && c <= 'Z';
	}

	private static boolean isdigit(int c)
	{
		return c >= '0' && c <= '9';
	}

	private static boolean isspace(int c)
	{
		return c <= ' ';
	}

	public LexState(LuaC state, InputStream stream)
	{
		_z = stream;
		_buff = new byte[32];
		_L = state;
	}

	void nextChar()
	{
		try
		{
			_current = _z.read();
		}
		catch(IOException e)
		{
			e.printStackTrace();
			_current = EOZ;
		}
	}

	boolean currIsNewline()
	{
		return _current == '\n' || _current == '\r';
	}

	void save_and_next()
	{
		save(_current);
		nextChar();
	}

	void save(int c)
	{
		if(_buff == null || _nbuff + 1 > _buff.length)
		    _buff = LuaC.realloc(_buff, _nbuff * 2 + 1);
		_buff[_nbuff++] = (byte)c;
	}

	static String token2str(int token)
	{
		if(token < FIRST_RESERVED)
		{
			return iscntrl(token) ?
			        LuaC.pushfstring("char(" + (token) + ")") :
			        LuaC.pushfstring(String.valueOf((char)token));
		}
		return luaX_tokens[token - FIRST_RESERVED];
	}

	private static boolean iscntrl(int token)
	{
		return token < ' ';
	}

	String txtToken(int token)
	{
		switch(token)
		{
			case TK_NAME:
			case TK_STRING:
			case TK_NUMBER:
				return new String(_buff, 0, _nbuff);
			default:
				return token2str(token);
		}
	}

	void lexerror(String msg, int token)
	{
		String cid = chunkid(_source.tojstring()); // TODO: get source name from source
		LuaC.pushfstring(cid + ":" + _linenumber + ": " + msg);
		if(token != 0)
		    LuaC.pushfstring("syntax error: " + msg + " near " + txtToken(token));
		throw new LuaError(cid + ":" + _linenumber + ": " + msg);
	}

	static String chunkid(String source)
	{
		if(source.startsWith("="))
		    return source.substring(1);
		String end = "";
		if(source.startsWith("@"))
		{
			source = source.substring(1);
		}
		else
		{
			source = "[string \"" + source;
			end = "\"]";
		}
		int n = source.length() + end.length();
		if(n > MAXSRC)
		    source = source.substring(0, MAXSRC - end.length() - 3) + "...";
		return source + end;
	}

	void syntaxerror(String msg)
	{
		lexerror(msg, _t.token);
	}

	// only called by new_localvarliteral() for var names.
	LuaString newstring(String s)
	{
		byte[] b = s.getBytes();
		return _L.newTString(b, 0, b.length);
	}

	LuaString newstring(byte[] bytes, int offset, int len)
	{
		return _L.newTString(bytes, offset, len);
	}

	void inclinenumber()
	{
		int old = _current;
		LuaC._assert(currIsNewline());
		nextChar(); /* skip '\n' or '\r' */
		if(currIsNewline() && _current != old)
		    nextChar(); /* skip '\n\r' or '\r\n' */
		if(++_linenumber >= MAX_INT)
		    syntaxerror("chunk has too many lines");
	}

	void setinput(LuaC L, int firstByte, InputStream z, LuaString source)
	{
		_decpoint = '.';
		_L = L;
		_lookahead.token = TK_EOS; /* no look-ahead token */
		_z = z;
		_fs = null;
		_linenumber = 1;
		_lastline = 1;
		_source = source;
		_nbuff = 0; /* initialize buffer */
		_current = firstByte; /* read first char */
		skipShebang();
	}

	private void skipShebang()
	{
		if(_current == '#')
		    while(!currIsNewline() && _current != EOZ)
			    nextChar();
	}

	/*
	** =======================================================
	** LEXICAL ANALYZER
	** =======================================================
	*/

	boolean check_next(String set)
	{
		if(set.indexOf(_current) < 0)
		    return false;
		save_and_next();
		return true;
	}

	void buffreplace(byte from, byte to)
	{
		int n = _nbuff;
		byte[] p = _buff;
		while((--n) >= 0)
			if(p[n] == from)
			    p[n] = to;
	}

	static boolean str2d(String str, SemInfo seminfo)
	{
		double d;
		str = str.trim(); // TODO: get rid of this
		if(str.startsWith("0x"))
		{
			d = Long.parseLong(str.substring(2), 16);
		}
		else
			d = Double.parseDouble(str);
		seminfo.r = LuaValue.valueOf(d);
		return true;
	}

	//
	// TODO: reexamine this source and see if it should be ported differently
	//
	// static void trydecpoint (LexState *ls, SemInfo *seminfo) {
	//	  /* format error: try to update decimal point separator */
	//	  struct lconv *cv = localeconv();
	//	  char old = decpoint;
	//	  decpoint = (cv ? cv->decimal_point[0] : '.');
	//	  buffreplace(ls, old, decpoint);  /* try updated decimal separator */
	//	  if (!luaO_str2d(luaZ_buffer(buff), &seminfo->r)) {
	//	    /* format error with correct decimal point: no more options */
	//	    buffreplace(ls, decpoint, '.');  /* undo change (for error message) */
	//	    luaX_lexerror(ls, "malformed number", TK_NUMBER);
	//	  }
	//	}
	//
	/*
	void trydecpoint(String str, SemInfo seminfo) {
		NumberFormat nf = NumberFormat.getInstance();
		try {
			Number n = nf.parse(str);
			double d = n.doubleValue();
			seminfo.r = new LDouble(d);
		} catch (ParseException e) {
			lexerror("malformed number", TK_NUMBER);
		}
	}
	*/

	void read_numeral(SemInfo seminfo)
	{
		LuaC._assert(isdigit(_current));
		do
		{
			save_and_next();
		}
		while(isdigit(_current) || _current == '.');
		if(check_next("Ee")) /* `E'? */
		    check_next("+-"); /* optional exponent sign */
		while(isalnum(_current) || _current == '_')
			save_and_next();
		save('\0');
		buffreplace((byte)'.', _decpoint); /* follow locale for decimal point */
		String str = new String(_buff, 0, _nbuff);
//		if (!str2d(str, seminfo)) /* format error? */
//			trydecpoint(str, seminfo); /* try to update decimal point separator */
		str2d(str, seminfo);
	}

	int skip_sep()
	{
		int count = 0;
		int s = _current;
		LuaC._assert(s == '[' || s == ']');
		save_and_next();
		while(_current == '=')
		{
			save_and_next();
			count++;
		}
		return (_current == s) ? count : (-count) - 1;
	}

	void read_long_string(SemInfo seminfo, int sep)
	{
		save_and_next(); /* skip 2nd `[' */
		if(currIsNewline()) /* string starts with a newline? */
		    inclinenumber(); /* skip it */
		for(boolean endloop = false; !endloop;)
		{
			switch(_current)
			{
				case EOZ:
					lexerror((seminfo != null) ? "unfinished long string"
					        : "unfinished long comment", TK_EOS);
					break; /* to avoid warnings */
				case '[':
				{
					if(skip_sep() == sep)
					    save_and_next(); /* skip 2nd `[' */
					break;
				}
				case ']':
				{
					if(skip_sep() == sep)
					{
						save_and_next(); /* skip 2nd `]' */
						endloop = true;
					}
					break;
				}
				case '\n':
				case '\r':
				{
					save('\n');
					inclinenumber();
					if(seminfo == null)
					    _nbuff = 0; /* avoid wasting space */
					break;
				}
				default:
				{
					if(seminfo != null)
						save_and_next();
					else
						nextChar();
				}
			}
		}
		if(seminfo != null)
		    seminfo.ts = newstring(_buff, 2 + sep, _nbuff - 2 * (2 + sep));
	}

	void read_string(int del, SemInfo seminfo)
	{
		save_and_next();
		while(_current != del)
		{
			switch(_current)
			{
				case EOZ:
					lexerror("unfinished string", TK_EOS);
					continue; /* to avoid warnings */
				case '\n':
				case '\r':
					lexerror("unfinished string", TK_STRING);
					continue; /* to avoid warnings */
				case '\\':
				{
					int c;
					nextChar(); /* do not save the `\' */
					switch(_current)
					{
						case 'a': /* bell */
							c = '\u0007';
							break;
						case 'b': /* backspace */
							c = '\b';
							break;
						case 'f': /* form feed */
							c = '\f';
							break;
						case 'n': /* newline */
							c = '\n';
							break;
						case 'r': /* carriage return */
							c = '\r';
							break;
						case 't': /* tab */
							c = '\t';
							break;
						case 'v': /* vertical tab */
							c = '\u000B';
							break;
						case '\n': /* go through */
						case '\r':
							save('\n');
							inclinenumber();
							continue;
						case EOZ:
							continue; /* will raise an error next loop */
						default:
						{
							if(!isdigit(_current))
								save_and_next(); /* handles \\, \", \', and \? */
							else
							{ /* \xxx */
								int i = 0;
								c = 0;
								do
								{
									c = 10 * c + (_current - '0');
									nextChar();
								}
								while(++i < 3 && isdigit(_current));
								if(c > UCHAR_MAX)
								    lexerror("escape sequence too large", TK_STRING);
								save(c);
							}
							continue;
						}
					}
					save(c);
					nextChar();
					continue;
				}
				default:
					save_and_next();
			}
		}
		save_and_next(); /* skip delimiter */
		seminfo.ts = newstring(_buff, 1, _nbuff - 2);
	}

	int llex(SemInfo seminfo)
	{
		_nbuff = 0;
		while(true)
		{
			switch(_current)
			{
				case '\n':
				case '\r':
				{
					inclinenumber();
					continue;
				}
				case '-':
				{
					nextChar();
					if(_current != '-')
					    return '-';
					/* else is a comment */
					nextChar();
					if(_current == '[')
					{
						int sep = skip_sep();
						_nbuff = 0; /* `skip_sep' may dirty the buffer */
						if(sep >= 0)
						{
							read_long_string(null, sep); /* long comment */
							_nbuff = 0;
							continue;
						}
					}
					/* else short comment */
					while(!currIsNewline() && _current != EOZ)
						nextChar();
					continue;
				}
				case '[':
				{
					int sep = skip_sep();
					if(sep >= 0)
					{
						read_long_string(seminfo, sep);
						return TK_STRING;
					}
					if(sep == -1)
					    return '[';
					lexerror("invalid long string delimiter", TK_STRING);
				}
				//$FALL-THROUGH$
				case '=':
				{
					nextChar();
					if(_current != '=')
					    return '=';
					nextChar();
					return TK_EQ;
				}
				case '<':
				{
					nextChar();
					if(_current != '=')
					    return '<';
					nextChar();
					return TK_LE;
				}
				case '>':
				{
					nextChar();
					if(_current != '=')
					    return '>';
					nextChar();
					return TK_GE;
				}
				case '~':
				{
					nextChar();
					if(_current != '=')
					    return '~';
					nextChar();
					return TK_NE;
				}
				case '"':
				case '\'':
				{
					read_string(_current, seminfo);
					return TK_STRING;
				}
				case '.':
				{
					save_and_next();
					if(check_next("."))
					{
						if(check_next("."))
						    return TK_DOTS; /* ... */
						return TK_CONCAT; /* .. */
					}
					if(!isdigit(_current))
					    return '.';
					read_numeral(seminfo);
					return TK_NUMBER;
				}
				case EOZ:
				{
					return TK_EOS;
				}
				default:
				{
					if(isspace(_current))
					{
						LuaC._assert(!currIsNewline());
						nextChar();
						continue;
					}
					else if(isdigit(_current))
					{
						read_numeral(seminfo);
						return TK_NUMBER;
					}
					else if(isalpha(_current) || _current == '_')
					{
						/* identifier or reserved word */
						LuaString ts;
						do
						{
							save_and_next();
						}
						while(isalnum(_current) || _current == '_');
						ts = newstring(_buff, 0, _nbuff);
						if(RESERVED.containsKey(ts))
						    return RESERVED.get(ts).intValue();
						seminfo.ts = ts;
						return TK_NAME;
					}
					else
					{
						int c = _current;
						nextChar();
						return c; /* single-char tokens (+ - / ...) */
					}
				}
			}
		}
	}

	void next()
	{
		_lastline = _linenumber;
		if(_lookahead.token != TK_EOS)
		{ /* is there a look-ahead token? */
			_t.set(_lookahead); /* use this one */
			_lookahead.token = TK_EOS; /* and discharge it */
		}
		else
			_t.token = llex(_t.seminfo); /* read next token */
	}

	void lookahead()
	{
		LuaC._assert(_lookahead.token == TK_EOS);
		_lookahead.token = llex(_lookahead.seminfo);
	}

	// =============================================================
	// from lcode.h
	// =============================================================

	// =============================================================
	// from lparser.c
	// =============================================================

	static class expdesc
	{
		int _k; // expkind, from enumerated list, above

		static class U
		{ // originally a union
			static class S
			{
				int info, aux;
			}

			final S          s = new S();
			private LuaValue _nval;

			public void setNval(LuaValue r)
			{
				_nval = r;
			}

			public LuaValue nval()
			{
				return (_nval == null ? LuaInteger.valueOf(s.info) : _nval);
			}
		}

		final U u = new U();
		int     t;          /* patch list of `exit when true' */
		int     f;          /* patch list of `exit when false' */

		void init(int k, int i)
		{
			f = NO_JUMP;
			t = NO_JUMP;
			_k = k;
			u.s.info = i;
		}

		boolean hasjumps()
		{
			return (t != f);
		}

		boolean isnumeral()
		{
			return (_k == VKNUM && t == NO_JUMP && f == NO_JUMP);
		}

		public void setvalue(expdesc other)
		{
			_k = other._k;
			u._nval = other.u._nval;
			u.s.info = other.u.s.info;
			u.s.aux = other.u.s.aux;
			t = other.t;
			f = other.f;
		}
	}

	static boolean hasmultret(int k)
	{
		return k == VCALL || k == VVARARG;
	}

	/*----------------------------------------------------------------------
	name		args	description
	------------------------------------------------------------------------*/

	/*
	 * * prototypes for recursive non-terminal functions
	 */

	void error_expected(int token)
	{
		syntaxerror(LuaC.pushfstring(LUA_QS(token2str(token)) + " expected"));
	}

	boolean testnext(int c)
	{
		if(_t.token == c)
		{
			next();
			return true;
		}
		return false;
	}

	void check(int c)
	{
		if(_t.token != c)
		    error_expected(c);
	}

	void checknext(int c)
	{
		check(c);
		next();
	}

	void check_condition(boolean c, String msg)
	{
		if(!(c))
		    syntaxerror(msg);
	}

	void check_match(int what, int who, int where)
	{
		if(!testnext(what))
		{
			if(where == _linenumber)
				error_expected(what);
			else
			{
				syntaxerror(LuaC.pushfstring(LUA_QS(token2str(what))
				        + " expected " + "(to close " + LUA_QS(token2str(who))
				        + " at line " + where + ")"));
			}
		}
	}

	LuaString str_checkname()
	{
		LuaString ts;
		check(TK_NAME);
		ts = _t.seminfo.ts;
		next();
		return ts;
	}

	void codestring(expdesc e, LuaString s)
	{
		e.init(VK, _fs.stringK(s));
	}

	void checkname(expdesc e)
	{
		codestring(e, str_checkname());
	}

	int registerlocalvar(LuaString varname)
	{
		FuncState fs = _fs;
		Prototype f = fs._f;
		if(f.locvars == null || fs.nlocvars + 1 > f.locvars.length)
		    f.locvars = LuaC.realloc(f.locvars, fs.nlocvars * 2 + 1);
		f.locvars[fs.nlocvars] = new LocVars(varname, 0, 0);
		return fs.nlocvars++;
	}

	void new_localvarliteral(String v, int n)
	{
		LuaString ts = newstring(v);
		new_localvar(ts, n);
	}

	void new_localvar(LuaString name, int n)
	{
		FuncState fs = _fs;
		fs.checklimit(fs.nactvar + n + 1, LuaC.LUAI_MAXVARS, "local variables");
		fs.actvar[fs.nactvar + n] = (short)registerlocalvar(name);
	}

	void adjustlocalvars(int nvars)
	{
		FuncState fs = _fs;
		fs.nactvar = (short)(fs.nactvar + nvars);
		for(; nvars > 0; nvars--)
		{
			fs.getlocvar(fs.nactvar - nvars)._startpc = fs._pc;
		}
	}

	void removevars(int tolevel)
	{
		FuncState fs = _fs;
		while(fs.nactvar > tolevel)
			fs.getlocvar(--fs.nactvar)._endpc = fs._pc;
	}

	void singlevar(expdesc var)
	{
		LuaString varname = str_checkname();
		FuncState fs = _fs;
		if(fs.singlevaraux(varname, var, 1) == VGLOBAL)
		    var.u.s.info = fs.stringK(varname); /* info points to global name */
	}

	void adjust_assign(int nvars, int nexps, expdesc e)
	{
		FuncState fs = _fs;
		int extra = nvars - nexps;
		if(hasmultret(e._k))
		{
			/* includes call itself */
			extra++;
			if(extra < 0)
			    extra = 0;
			/* last exp. provides the difference */
			fs.setreturns(e, extra);
			if(extra > 1)
			    fs.reserveregs(extra - 1);
		}
		else
		{
			/* close last expression */
			if(e._k != VVOID)
			    fs.exp2nextreg(e);
			if(extra > 0)
			{
				int reg = fs.freereg;
				fs.reserveregs(extra);
				fs.nil(reg, extra);
			}
		}
	}

	void enterlevel()
	{
		if(++_L._nCcalls > LUAI_MAXCCALLS)
		    lexerror("chunk has too many syntax levels", 0);
	}

	void leavelevel()
	{
		_L._nCcalls--;
	}

	void pushclosure(FuncState func, expdesc v)
	{
		FuncState fs = _fs;
		Prototype f = fs._f;
		if(f.p == null || fs.np + 1 > f.p.length)
		    f.p = LuaC.realloc(f.p, fs.np * 2 + 1);
		f.p[fs.np++] = func._f;
		v.init(VRELOCABLE, fs.codeABx(Lua.OP_CLOSURE, 0, fs.np - 1));
		for(int i = 0; i < func._f.nups; i++)
		{
			int o = (func.upvalues[i].k == VLOCAL) ? Lua.OP_MOVE
			        : Lua.OP_GETUPVAL;
			fs.codeABC(o, 0, func.upvalues[i].info, 0);
		}
	}

	void open_func(FuncState fs)
	{
		LuaC L = _L;
		Prototype f = new Prototype();
		if(_fs != null)
		    f.source = _fs._f.source;
		fs._f = f;
		fs.prev = _fs; /* linked list of funcstates */
		fs.ls = this;
		fs.L = L;
		_fs = fs;
		fs._pc = 0;
		fs.lasttarget = -1;
		fs._jpc = NO_JUMP;
		fs.freereg = 0;
		fs.nk = 0;
		fs.np = 0;
		fs.nlocvars = 0;
		fs.nactvar = 0;
		fs._bl = null;
		f.maxstacksize = 2; /* registers 0/1 are always valid */
		//fs.h = new LTable();
		fs.htable = new HashMap<LuaValue, Integer>();
	}

	void close_func()
	{
		FuncState fs = _fs;
		Prototype f = fs._f;
		removevars(0);
		fs.ret(0, 0); /* final return */
		f.code = LuaC.realloc(f.code, fs._pc);
		f.lineinfo = LuaC.realloc(f.lineinfo, fs._pc);
		// f.sizelineinfo = fs.pc;
		f.k = LuaC.realloc(f.k, fs.nk);
		f.p = LuaC.realloc(f.p, fs.np);
		f.locvars = LuaC.realloc(f.locvars, fs.nlocvars);
		// f.sizelocvars = fs.nlocvars;
		f.upvalues = LuaC.realloc(f.upvalues, f.nups);
		// LuaC._assert (CheckCode.checkcode(f));
		LuaC._assert(fs._bl == null);
		_fs = fs.prev;
//		L.top -= 2; /* remove table and prototype from the stack */
		// /* last token read was anchored in defunct function; must reanchor it
		// */
		// if (fs!=null) ls.anchor_token();
	}

	/*============================================================*/
	/* GRAMMAR RULES */
	/*============================================================*/

	void field(expdesc v)
	{
		/* field -> ['.' | ':'] NAME */
		FuncState fs = _fs;
		expdesc key = new expdesc();
		fs.exp2anyreg(v);
		next(); /* skip the dot or colon */
		checkname(key);
		fs.indexed(v, key);
	}

	void yindex(expdesc v)
	{
		/* index -> '[' expr ']' */
		next(); /* skip the '[' */
		expr(v);
		_fs.exp2val(v);
		checknext(']');
	}

	/*
	** {======================================================================
	** Rules for Constructors
	** =======================================================================
	*/

	static class ConsControl
	{
		expdesc v = new expdesc(); /* last list item read */
		expdesc t;                /* table descriptor */
		int     nh;               /* total number of `record' elements */
		int     na;               /* total number of array elements */
		int     tostore;          /* number of array elements pending to be stored */
	}

	void recfield(ConsControl cc)
	{
		/* recfield -> (NAME | `['exp1`]') = exp1 */
		FuncState fs = _fs;
		int reg = _fs.freereg;
		expdesc key = new expdesc();
		expdesc val = new expdesc();
		int rkkey;
		if(_t.token == TK_NAME)
		{
			fs.checklimit(cc.nh, MAX_INT, "items in a constructor");
			checkname(key);
		}
		else
			/* _t.token == '[' */
			yindex(key);
		cc.nh++;
		checknext('=');
		rkkey = fs.exp2RK(key);
		expr(val);
		fs.codeABC(Lua.OP_SETTABLE, cc.t.u.s.info, rkkey, fs.exp2RK(val));
		fs.freereg = reg; /* free registers */
	}

	void listfield(ConsControl cc)
	{
		expr(cc.v);
		_fs.checklimit(cc.na, MAX_INT, "items in a constructor");
		cc.na++;
		cc.tostore++;
	}

	void constructor(expdesc t)
	{
		/* constructor -> ?? */
		FuncState fs = _fs;
		int line = _linenumber;
		int pc = fs.codeABC(Lua.OP_NEWTABLE, 0, 0, 0);
		ConsControl cc = new ConsControl();
		cc.na = cc.nh = cc.tostore = 0;
		cc.t = t;
		t.init(VRELOCABLE, pc);
		cc.v.init(VVOID, 0); /* no value (yet) */
		fs.exp2nextreg(t); /* fix it at stack top (for gc) */
		checknext('{');
		do
		{
			LuaC._assert(cc.v._k == VVOID || cc.tostore > 0);
			if(_t.token == '}')
			    break;
			fs.closelistfield(cc);
			switch(_t.token)
			{
				case TK_NAME:
				{ /* may be listfields or recfields */
					lookahead();
					if(_lookahead.token != '=') /* expression? */
						listfield(cc);
					else
						recfield(cc);
					break;
				}
				case '[':
				{ /* constructor_item -> recfield */
					recfield(cc);
					break;
				}
				default:
				{ /* constructor_part -> listfield */
					listfield(cc);
					break;
				}
			}
		}
		while(testnext(',') || testnext(';'));
		check_match('}', '{', line);
		fs.lastlistfield(cc);
		InstructionPtr i = new InstructionPtr(fs._f.code, pc);
		LuaC.SETARG_B(i, luaO_int2fb(cc.na)); /* set initial array size */
		LuaC.SETARG_C(i, luaO_int2fb(cc.nh)); /* set initial table size */
	}

	/*
	** converts an integer to a "floating point byte", represented as
	** (eeeeexxx), where the real value is (1xxx) * 2^(eeeee - 1) if
	** eeeee != 0 and (xxx) otherwise.
	*/
	static int luaO_int2fb(int x)
	{
		int e = 0; /* expoent */
		while(x >= 16)
		{
			x = (x + 1) >> 1;
			e++;
		}
		if(x < 8)
		    return x;
		return ((e + 1) << 3) | ((x) - 8);
	}

	/* }====================================================================== */

	void parlist()
	{
		/* parlist -> [ param { `,' param } ] */
		FuncState fs = _fs;
		Prototype f = fs._f;
		int nparams = 0;
		f.is_vararg = 0;
		if(_t.token != ')')
		{ /* is `parlist' not empty? */
			do
			{
				switch(_t.token)
				{
					case TK_NAME:
					{ /* param . NAME */
						new_localvar(str_checkname(), nparams++);
						break;
					}
					case TK_DOTS:
					{ /* param . `...' */
						next();
						if(LUA_COMPAT_VARARG)
						{
							/* use `arg' as default name */
							new_localvarliteral("arg", nparams++);
							f.is_vararg = Lua.VARARG_HASARG | Lua.VARARG_NEEDSARG;
						}
						f.is_vararg |= Lua.VARARG_ISVARARG;
						break;
					}
					default:
						syntaxerror("<name> or " + LUA_QL("...") + " expected");
				}
			}
			while((f.is_vararg == 0) && testnext(','));
		}
		adjustlocalvars(nparams);
		f.numparams = (fs.nactvar - (f.is_vararg & Lua.VARARG_HASARG));
		fs.reserveregs(fs.nactvar); /* reserve register for parameters */
	}

	void body(expdesc e, boolean needself, int line)
	{
		/* body -> `(' parlist `)' chunk END */
		FuncState new_fs = new FuncState();
		open_func(new_fs);
		new_fs._f.linedefined = line;
		checknext('(');
		if(needself)
		{
			new_localvarliteral("self", 0);
			adjustlocalvars(1);
		}
		parlist();
		checknext(')');
		chunk();
		new_fs._f.lastlinedefined = _linenumber;
		check_match(TK_END, TK_FUNCTION, line);
		close_func();
		pushclosure(new_fs, e);
	}

	int explist1(expdesc v)
	{
		/* explist1 -> expr { `,' expr } */
		int n = 1; /* at least one expression */
		expr(v);
		while(testnext(','))
		{
			_fs.exp2nextreg(v);
			expr(v);
			n++;
		}
		return n;
	}

	void funcargs(expdesc f)
	{
		FuncState fs = _fs;
		expdesc args = new expdesc();
		int base, nparams;
		int line = _linenumber;
		switch(_t.token)
		{
			case '(':
			{ /* funcargs -> `(' [ explist1 ] `)' */
				if(line != _lastline)
				    syntaxerror("ambiguous syntax (function call x new statement)");
				next();
				if(_t.token == ')') /* arg list is empty? */
					args._k = VVOID;
				else
				{
					explist1(args);
					fs.setmultret(args);
				}
				check_match(')', '(', line);
				break;
			}
			case '{':
			{ /* funcargs -> constructor */
				constructor(args);
				break;
			}
			case TK_STRING:
			{ /* funcargs -> STRING */
				codestring(args, _t.seminfo.ts);
				next(); /* must use `seminfo' before `next' */
				break;
			}
			default:
			{
				syntaxerror("function arguments expected");
				return;
			}
		}
		LuaC._assert(f._k == VNONRELOC);
		base = f.u.s.info; /* base register for call */
		if(hasmultret(args._k))
			nparams = Lua.LUA_MULTRET; /* open call */
		else
		{
			if(args._k != VVOID)
			    fs.exp2nextreg(args); /* close last argument */
			nparams = fs.freereg - (base + 1);
		}
		f.init(VCALL, fs.codeABC(Lua.OP_CALL, base, nparams + 1, 2));
		fs.fixline(line);
		fs.freereg = base + 1; /* call remove function and arguments and leaves
		                       * (unless changed) one result */
	}

	/*
	** {======================================================================
	** Expression parsing
	** =======================================================================
	*/

	void prefixexp(expdesc v)
	{
		/* prefixexp -> NAME | '(' expr ')' */
		switch(_t.token)
		{
			case '(':
			{
				int line = _linenumber;
				next();
				expr(v);
				check_match(')', '(', line);
				_fs.dischargevars(v);
				return;
			}
			case TK_NAME:
			{
				singlevar(v);
				return;
			}
			default:
			{
				syntaxerror("unexpected symbol");
				return;
			}
		}
	}

	void primaryexp(expdesc v)
	{
		/*
		 * primaryexp -> prefixexp { `.' NAME | `[' exp `]' | `:' NAME funcargs |
		 * funcargs }
		 */
		FuncState fs = _fs;
		prefixexp(v);
		for(;;)
		{
			switch(_t.token)
			{
				case '.':
				{ /* field */
					field(v);
					break;
				}
				case '[':
				{ /* `[' exp1 `]' */
					expdesc key = new expdesc();
					fs.exp2anyreg(v);
					yindex(key);
					fs.indexed(v, key);
					break;
				}
				case ':':
				{ /* `:' NAME funcargs */
					expdesc key = new expdesc();
					next();
					checkname(key);
					fs.self(v, key);
					funcargs(v);
					break;
				}
				case '(':
				case TK_STRING:
				case '{':
				{ /* funcargs */
					fs.exp2nextreg(v);
					funcargs(v);
					break;
				}
				default:
					return;
			}
		}
	}

	void simpleexp(expdesc v)
	{
		/*
		 * simpleexp -> NUMBER | STRING | NIL | true | false | ... | constructor |
		 * FUNCTION body | primaryexp
		 */
		switch(_t.token)
		{
			case TK_NUMBER:
			{
				v.init(VKNUM, 0);
				v.u.setNval(_t.seminfo.r);
				break;
			}
			case TK_STRING:
			{
				codestring(v, _t.seminfo.ts);
				break;
			}
			case TK_NIL:
			{
				v.init(VNIL, 0);
				break;
			}
			case TK_TRUE:
			{
				v.init(VTRUE, 0);
				break;
			}
			case TK_FALSE:
			{
				v.init(VFALSE, 0);
				break;
			}
			case TK_DOTS:
			{ /* vararg */
				FuncState fs = _fs;
				check_condition(fs._f.is_vararg != 0, "cannot use " + LUA_QL("...")
				        + " outside a vararg function");
				fs._f.is_vararg &= ~Lua.VARARG_NEEDSARG; /* don't need 'arg' */
				v.init(VVARARG, fs.codeABC(Lua.OP_VARARG, 0, 1, 0));
				break;
			}
			case '{':
			{ /* constructor */
				constructor(v);
				return;
			}
			case TK_FUNCTION:
			{
				next();
				body(v, false, _linenumber);
				return;
			}
			default:
			{
				primaryexp(v);
				return;
			}
		}
		next();
	}

	static int getunopr(int op)
	{
		switch(op)
		{
			case TK_NOT:
				return OPR_NOT;
			case '-':
				return OPR_MINUS;
			case '#':
				return OPR_LEN;
			default:
				return OPR_NOUNOPR;
		}
	}

	static int getbinopr(int op)
	{
		switch(op)
		{
			case '+':
				return OPR_ADD;
			case '-':
				return OPR_SUB;
			case '*':
				return OPR_MUL;
			case '/':
				return OPR_DIV;
			case '%':
				return OPR_MOD;
			case '^':
				return OPR_POW;
			case TK_CONCAT:
				return OPR_CONCAT;
			case TK_NE:
				return OPR_NE;
			case TK_EQ:
				return OPR_EQ;
			case '<':
				return OPR_LT;
			case TK_LE:
				return OPR_LE;
			case '>':
				return OPR_GT;
			case TK_GE:
				return OPR_GE;
			case TK_AND:
				return OPR_AND;
			case TK_OR:
				return OPR_OR;
			default:
				return OPR_NOBINOPR;
		}
	}

	private static final class Priority
	{
		final byte left; /* left priority for each binary operator */

		final byte right; /* right priority */

		public Priority(int i, int j)
		{
			left = (byte)i;
			right = (byte)j;
		}
	}

	private static final Priority[] PRIORITY       = { /* ORDER OPR */
	                                               new Priority(6, 6), new Priority(6, 6), new Priority(7, 7), new Priority(7, 7), new Priority(7, 7), /* `+' `-' `/' `%' */
	                                               new Priority(10, 9), new Priority(5, 4), /* power and concat (right associative) */
	                                               new Priority(3, 3), new Priority(3, 3), /* equality and inequality */
	                                               new Priority(3, 3), new Priority(3, 3), new Priority(3, 3), new Priority(3, 3), /* order */
	                                               new Priority(2, 2), new Priority(1, 1) /* logical (and/or) */
	                                               };

	private static final int        UNARY_PRIORITY = 8; /* priority for unary operators */

	/*
	** subexpr -> (simpleexp | unop subexpr) { binop subexpr }
	** where `binop' is any binary operator with a priority higher than `limit'
	*/
	int subexpr(expdesc v, int limit)
	{
		int op;
		int uop;
		enterlevel();
		uop = getunopr(_t.token);
		if(uop != OPR_NOUNOPR)
		{
			next();
			subexpr(v, UNARY_PRIORITY);
			_fs.prefix(uop, v);
		}
		else
			simpleexp(v);
		/* expand while operators have priorities higher than `limit' */
		op = getbinopr(_t.token);
		while(op != OPR_NOBINOPR && PRIORITY[op].left > limit)
		{
			expdesc v2 = new expdesc();
			int nextop;
			next();
			_fs.infix(op, v);
			/* read sub-expression with higher priority */
			nextop = subexpr(v2, PRIORITY[op].right);
			_fs.posfix(op, v, v2);
			op = nextop;
		}
		leavelevel();
		return op; /* return first untreated operator */
	}

	void expr(expdesc v)
	{
		subexpr(v, 0);
	}

	/* }==================================================================== */

	/*
	** {======================================================================
	** Rules for Statements
	** =======================================================================
	*/
	static boolean block_follow(int token)
	{
		switch(token)
		{
			case TK_ELSE:
			case TK_ELSEIF:
			case TK_END:
			case TK_UNTIL:
			case TK_EOS:
				return true;
			default:
				return false;
		}
	}

	void block()
	{
		/* block -> chunk */
		FuncState fs = _fs;
		BlockCnt bl = new BlockCnt();
		fs.enterblock(bl, false);
		chunk();
		LuaC._assert(bl.breaklist == NO_JUMP);
		fs.leaveblock();
	}

	/*
	** structure to chain all variables in the left-hand side of an
	** assignment
	*/
	static class LHS_assign
	{
		LHS_assign prev;
		/* variable (global, local, upvalue, or indexed) */
		expdesc    v = new expdesc();
	}

	/*
	** check whether, in an assignment to a local variable, the local variable
	** is needed in a previous assignment (to a table). If so, save original
	** local value in a safe place and use this safe copy in the previous
	** assignment.
	*/
	void check_conflict(LHS_assign lh, expdesc v)
	{
		FuncState fs = _fs;
		int extra = fs.freereg; /* eventual position to save local variable */
		boolean conflict = false;
		for(; lh != null; lh = lh.prev)
		{
			if(lh.v._k == VINDEXED)
			{
				if(lh.v.u.s.info == v.u.s.info)
				{ /* conflict? */
					conflict = true;
					lh.v.u.s.info = extra; /* previous assignment will use safe copy */
				}
				if(lh.v.u.s.aux == v.u.s.info)
				{ /* conflict? */
					conflict = true;
					lh.v.u.s.aux = extra; /* previous assignment will use safe copy */
				}
			}
		}
		if(conflict)
		{
			fs.codeABC(Lua.OP_MOVE, fs.freereg, v.u.s.info, 0); /* make copy */
			fs.reserveregs(1);
		}
	}

	void assignment(LHS_assign lh, int nvars)
	{
		expdesc e = new expdesc();
		check_condition(VLOCAL <= lh.v._k && lh.v._k <= VINDEXED,
		        "syntax error");
		if(testnext(','))
		{ /* assignment -> `,' primaryexp assignment */
			LHS_assign nv = new LHS_assign();
			nv.prev = lh;
			primaryexp(nv.v);
			if(nv.v._k == VLOCAL)
			    check_conflict(lh, nv.v);
			assignment(nv, nvars + 1);
		}
		else
		{ /* assignment . `=' explist1 */
			int nexps;
			checknext('=');
			nexps = explist1(e);
			if(nexps != nvars)
			{
				adjust_assign(nvars, nexps, e);
				if(nexps > nvars)
				    _fs.freereg -= nexps - nvars; /* remove extra values */
			}
			else
			{
				_fs.setoneret(e); /* close last expression */
				_fs.storevar(lh.v, e);
				return; /* avoid default */
			}
		}
		e.init(VNONRELOC, _fs.freereg - 1); /* default assignment */
		_fs.storevar(lh.v, e);
	}

	int cond()
	{
		/* cond -> exp */
		expdesc v = new expdesc();
		/* read condition */
		expr(v);
		/* `falses' are all equal here */
		if(v._k == VNIL)
		    v._k = VFALSE;
		_fs.goiftrue(v);
		return v.f;
	}

	@SuppressWarnings("null")
	void breakstat()
	{
		FuncState fs = _fs;
		BlockCnt bl = fs._bl;
		boolean upval = false;
		while(bl != null && !bl.isbreakable)
		{
			upval |= bl.upval;
			bl = bl.previous;
		}
		if(bl == null)
		    syntaxerror("no loop to break");
		if(upval)
		    fs.codeABC(Lua.OP_CLOSE, bl.nactvar, 0, 0);
		bl.breaklist = fs.concat(bl.breaklist, fs.jump());
	}

	void whilestat(int line)
	{
		/* whilestat -> WHILE cond DO block END */
		FuncState fs = _fs;
		int whileinit;
		int condexit;
		BlockCnt bl = new BlockCnt();
		next(); /* skip WHILE */
		whileinit = fs.getlabel();
		condexit = cond();
		fs.enterblock(bl, true);
		checknext(TK_DO);
		block();
		fs.patchlist(fs.jump(), whileinit);
		check_match(TK_END, TK_WHILE, line);
		fs.leaveblock();
		fs.patchtohere(condexit); /* false conditions finish the loop */
	}

	void repeatstat(int line)
	{
		/* repeatstat -> REPEAT block UNTIL cond */
		int condexit;
		FuncState fs = _fs;
		int repeat_init = fs.getlabel();
		BlockCnt bl1 = new BlockCnt();
		BlockCnt bl2 = new BlockCnt();
		fs.enterblock(bl1, true); /* loop block */
		fs.enterblock(bl2, false); /* scope block */
		next(); /* skip REPEAT */
		chunk();
		check_match(TK_UNTIL, TK_REPEAT, line);
		condexit = cond(); /* read condition (inside scope block) */
		if(!bl2.upval)
		{ /* no upvalues? */
			fs.leaveblock(); /* finish scope */
			fs.patchlist(condexit, repeat_init); /* close the loop */
		}
		else
		{ /* complete semantics when there are upvalues */
			breakstat(); /* if condition then break */
			fs.patchtohere(condexit); /* else... */
			fs.leaveblock(); /* finish scope... */
			fs.patchlist(fs.jump(), repeat_init); /* and repeat */
		}
		fs.leaveblock(); /* finish loop */
	}

	int exp1()
	{
		expdesc e = new expdesc();
		int k;
		expr(e);
		k = e._k;
		_fs.exp2nextreg(e);
		return k;
	}

	void forbody(int base, int line, int nvars, boolean isnum)
	{
		/* forbody -> DO block */
		BlockCnt bl = new BlockCnt();
		FuncState fs = _fs;
		int prep, endfor;
		adjustlocalvars(3); /* control variables */
		checknext(TK_DO);
		prep = isnum ? fs.codeAsBx(Lua.OP_FORPREP, base, NO_JUMP) : fs.jump();
		fs.enterblock(bl, false); /* scope for declared variables */
		adjustlocalvars(nvars);
		fs.reserveregs(nvars);
		block();
		fs.leaveblock(); /* end of scope for declared variables */
		fs.patchtohere(prep);
		endfor = (isnum) ? fs.codeAsBx(Lua.OP_FORLOOP, base, NO_JUMP) : fs
		        .codeABC(Lua.OP_TFORLOOP, base, 0, nvars);
		fs.fixline(line); /* pretend that `Lua.OP_FOR' starts the loop */
		fs.patchlist((isnum ? endfor : fs.jump()), prep + 1);
	}

	void fornum(LuaString varname, int line)
	{
		/* fornum -> NAME = exp1,exp1[,exp1] forbody */
		FuncState fs = _fs;
		int base = fs.freereg;
		new_localvarliteral(RESERVED_LOCAL_VAR_FOR_INDEX, 0);
		new_localvarliteral(RESERVED_LOCAL_VAR_FOR_LIMIT, 1);
		new_localvarliteral(RESERVED_LOCAL_VAR_FOR_STEP, 2);
		new_localvar(varname, 3);
		checknext('=');
		exp1(); /* initial value */
		checknext(',');
		exp1(); /* limit */
		if(testnext(','))
			exp1(); /* optional step */
		else
		{ /* default step = 1 */
			fs.codeABx(Lua.OP_LOADK, fs.freereg, fs.numberK(LuaInteger.valueOf(1)));
			fs.reserveregs(1);
		}
		forbody(base, line, 1, true);
	}

	void forlist(LuaString indexname)
	{
		/* forlist -> NAME {,NAME} IN explist1 forbody */
		FuncState fs = _fs;
		expdesc e = new expdesc();
		int nvars = 0;
		int line;
		int base = fs.freereg;
		/* create control variables */
		new_localvarliteral(RESERVED_LOCAL_VAR_FOR_GENERATOR, nvars++);
		new_localvarliteral(RESERVED_LOCAL_VAR_FOR_STATE, nvars++);
		new_localvarliteral(RESERVED_LOCAL_VAR_FOR_CONTROL, nvars++);
		/* create declared variables */
		new_localvar(indexname, nvars++);
		while(testnext(','))
			new_localvar(str_checkname(), nvars++);
		checknext(TK_IN);
		line = _linenumber;
		adjust_assign(3, explist1(e), e);
		fs.checkstack(3); /* extra space to call generator */
		forbody(base, line, nvars - 3, false);
	}

	void forstat(int line)
	{
		/* forstat -> FOR (fornum | forlist) END */
		FuncState fs = _fs;
		LuaString varname;
		BlockCnt bl = new BlockCnt();
		fs.enterblock(bl, true); /* scope for loop and control variables */
		next(); /* skip `for' */
		varname = str_checkname(); /* first variable name */
		switch(_t.token)
		{
			case '=':
				fornum(varname, line);
				break;
			case ',':
			case TK_IN:
				forlist(varname);
				break;
			default:
				syntaxerror(LUA_QL("=") + " or " + LUA_QL("in") + " expected");
		}
		check_match(TK_END, TK_FOR, line);
		fs.leaveblock(); /* loop scope (`break' jumps to this point) */
	}

	int test_then_block()
	{
		/* test_then_block -> [IF | ELSEIF] cond THEN block */
		int condexit;
		next(); /* skip IF or ELSEIF */
		condexit = cond();
		checknext(TK_THEN);
		block(); /* `then' part */
		return condexit;
	}

	void ifstat(int line)
	{
		/* ifstat -> IF cond THEN block {ELSEIF cond THEN block} [ELSE block]
		 * END */
		FuncState fs = _fs;
		int flist;
		int escapelist = NO_JUMP;
		flist = test_then_block(); /* IF cond THEN block */
		while(_t.token == TK_ELSEIF)
		{
			escapelist = fs.concat(escapelist, fs.jump());
			fs.patchtohere(flist);
			flist = test_then_block(); /* ELSEIF cond THEN block */
		}
		if(_t.token == TK_ELSE)
		{
			escapelist = fs.concat(escapelist, fs.jump());
			fs.patchtohere(flist);
			next(); /* skip ELSE (after patch, for correct line info) */
			block(); /* `else' part */
		}
		else
			escapelist = fs.concat(escapelist, flist);
		fs.patchtohere(escapelist);
		check_match(TK_END, TK_IF, line);
	}

	void localfunc()
	{
		expdesc v = new expdesc();
		expdesc b = new expdesc();
		FuncState fs = _fs;
		new_localvar(str_checkname(), 0);
		v.init(VLOCAL, fs.freereg);
		fs.reserveregs(1);
		adjustlocalvars(1);
		body(b, false, _linenumber);
		fs.storevar(v, b);
		/* debug information will only see the variable after this point! */
		fs.getlocvar(fs.nactvar - 1)._startpc = fs._pc;
	}

	void localstat()
	{
		/* stat -> LOCAL NAME {`,' NAME} [`=' explist1] */
		int nvars = 0;
		int nexps;
		expdesc e = new expdesc();
		do
		{
			new_localvar(str_checkname(), nvars++);
		}
		while(testnext(','));
		if(testnext('='))
			nexps = explist1(e);
		else
		{
			e._k = VVOID;
			nexps = 0;
		}
		adjust_assign(nvars, nexps, e);
		adjustlocalvars(nvars);
	}

	boolean funcname(expdesc v)
	{
		/* funcname -> NAME {field} [`:' NAME] */
		boolean needself = false;
		singlevar(v);
		while(_t.token == '.')
			field(v);
		if(_t.token == ':')
		{
			needself = true;
			field(v);
		}
		return needself;
	}

	void funcstat(int line)
	{
		/* funcstat -> FUNCTION funcname body */
		boolean needself;
		expdesc v = new expdesc();
		expdesc b = new expdesc();
		next(); /* skip FUNCTION */
		needself = funcname(v);
		body(b, needself, line);
		_fs.storevar(v, b);
		_fs.fixline(line); /* definition `happens' in the first line */
	}

	void exprstat()
	{
		/* stat -> func | assignment */
		FuncState fs = _fs;
		LHS_assign v = new LHS_assign();
		primaryexp(v.v);
		if(v.v._k == VCALL) /* stat -> func */
			LuaC.SETARG_C(fs.getcodePtr(v.v), 1); /* call statement uses no results */
		else
		{ /* stat -> assignment */
			v.prev = null;
			assignment(v, 1);
		}
	}

	void retstat()
	{
		/* stat -> RETURN explist */
		FuncState fs = _fs;
		expdesc e = new expdesc();
		int first, nret; /* registers with returned values */
		next(); /* skip RETURN */
		if(block_follow(_t.token) || _t.token == ';')
			first = nret = 0; /* return no values */
		else
		{
			nret = explist1(e); /* optional return values */
			if(hasmultret(e._k))
			{
				fs.setmultret(e);
				if(e._k == VCALL && nret == 1)
				{ /* tail call? */
					LuaC.SET_OPCODE(fs.getcodePtr(e), Lua.OP_TAILCALL);
					LuaC._assert(Lua.GETARG_A(fs.getcode(e)) == fs.nactvar);
				}
				first = fs.nactvar;
				nret = Lua.LUA_MULTRET; /* return all values */
			}
			else
			{
				if(nret == 1) /* only one single value? */
					first = fs.exp2anyreg(e);
				else
				{
					fs.exp2nextreg(e); /* values must go to the `stack' */
					first = fs.nactvar; /* return all `active' values */
					LuaC._assert(nret == fs.freereg - first);
				}
			}
		}
		fs.ret(first, nret);
	}

	boolean statement()
	{
		int line = _linenumber; /* may be needed for error messages */
		switch(_t.token)
		{
			case TK_IF:
			{ /* stat -> ifstat */
				ifstat(line);
				return false;
			}
			case TK_WHILE:
			{ /* stat -> whilestat */
				whilestat(line);
				return false;
			}
			case TK_DO:
			{ /* stat -> DO block END */
				next(); /* skip DO */
				block();
				check_match(TK_END, TK_DO, line);
				return false;
			}
			case TK_FOR:
			{ /* stat -> forstat */
				forstat(line);
				return false;
			}
			case TK_REPEAT:
			{ /* stat -> repeatstat */
				repeatstat(line);
				return false;
			}
			case TK_FUNCTION:
			{
				funcstat(line); /* stat -> funcstat */
				return false;
			}
			case TK_LOCAL:
			{ /* stat -> localstat */
				next(); /* skip LOCAL */
				if(testnext(TK_FUNCTION)) /* local function? */
					localfunc();
				else
					localstat();
				return false;
			}
			case TK_RETURN:
			{ /* stat -> retstat */
				retstat();
				return true; /* must be last statement */
			}
			case TK_BREAK:
			{ /* stat -> breakstat */
				next(); /* skip BREAK */
				breakstat();
				return true; /* must be last statement */
			}
			default:
			{
				exprstat();
				return false; /* to avoid warnings */
			}
		}
	}

	void chunk()
	{
		/* chunk -> { stat [`;'] } */
		boolean islast = false;
		enterlevel();
		while(!islast && !block_follow(_t.token))
		{
			islast = statement();
			testnext(';');
			LuaC._assert(_fs._f.maxstacksize >= _fs.freereg
			        && _fs.freereg >= _fs.nactvar);
			_fs.freereg = _fs.nactvar; /* free registers */
		}
		leavelevel();
	}

	/* }====================================================================== */
}
