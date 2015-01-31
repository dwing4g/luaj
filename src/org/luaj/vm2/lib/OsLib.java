package org.luaj.vm2.lib;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Subclass of {@link LibFunction} which implements the standard lua {@code os} library.
 * <p>
 * It is a usable base with simplified stub functions
 * for library functions that cannot be implemented uniformly
 * on Jse and Jme.
 * <p>
 * This can be installed as-is on either platform, or extended
 * and refined to be used in a complete Jse implementation.
 * <p>
 * Because the nature of the {@code os} library is to encapsulate
 * os-specific features, the behavior of these functions varies considerably
 * from their counterparts in the C platform.
 * <p>
 * The following functions have limited implementations of features
 * that are not supported well on Jme:
 * <ul>
 * <li>{@code execute()}</li>
 * <li>{@code remove()}</li>
 * <li>{@code rename()}</li>
 * <li>{@code tmpname()}</li>
 * </ul>
 * <p>
 * Typically, this library is included as part of a call to either
 * {@link JmePlatform#standardGlobals()}
 * <p>
 * To instantiate and use it directly,
 * link it into your globals table via {@link LuaValue#load(LuaValue)} using code such as:
 * <pre> {@code
 * LuaTable _G = new LuaTable();
 * LuaThread.setGlobals(_G);
 * _G.load(new BaseLib());
 * _G.load(new PackageLib());
 * _G.load(new OsLib());
 * System.out.println( _G.get("os").get("time").call() );
 * } </pre>
 * Doing so will ensure the library is properly initialized
 * and loaded into the globals table.
 * <p>
 * @see LibFunction
 * @see JsePlatform
 * @see <a href="http://www.lua.org/manual/5.1/manual.html#5.8">http://www.lua.org/manual/5.1/manual.html#5.8</a>
 */
public class OsLib extends VarArgFunction
{
	public static String          TMP_PREFIX       = ".luaj";
	public static String          TMP_SUFFIX       = "tmp";

	private static final int      INIT             = 0;
	private static final int      CLOCK            = 1;
	private static final int      DATE             = 2;
	private static final int      DIFFTIME         = 3;
	private static final int      EXECUTE          = 4;
	private static final int      EXIT             = 5;
	private static final int      GETENV           = 6;
	private static final int      REMOVE           = 7;
	private static final int      RENAME           = 8;
	private static final int      SETLOCALE        = 9;
	private static final int      TIME             = 10;
	private static final int      TMPNAME          = 11;

	private static final String[] NAMES            = {
	                                               "clock",
	                                               "date",
	                                               "difftime",
	                                               "execute",
	                                               "exit",
	                                               "getenv",
	                                               "remove",
	                                               "rename",
	                                               "setlocale",
	                                               "time",
	                                               "tmpname",
	                                               };

	private static final long     t0               = System.currentTimeMillis();
	private static AtomicLong     tmpnames         = new AtomicLong(t0);

	/** return code indicating the execute() threw an I/O exception */
	public static int             EXEC_IOEXCEPTION = 1;

	/** return code indicating the execute() was interrupted */
	public static int             EXEC_INTERRUPTED = -2;

	/** return code indicating the execute() threw an unknown exception */
	public static int             EXEC_ERROR       = -3;

	/**
	 * Create and OsLib instance.
	 */
	public OsLib()
	{
	}

	public LuaValue init()
	{
		LuaTable t = new LuaTable();
		bind(t, this.getClass(), NAMES, CLOCK);
		env.set("os", t);
		PackageLib.instance.LOADED.set("os", t);
		return t;
	}

	@Override
	public Varargs invoke(Varargs args)
	{
		try
		{
			switch(_opcode)
			{
				case INIT:
					return init();
				case CLOCK:
					return valueOf(clock());
				case DATE:
				{
					String s = args.optjstring(1, null);
					double t = args.optdouble(2, -1);
					return valueOf(date(s, t == -1 ? System.currentTimeMillis() / 1000. : t));
				}
				case DIFFTIME:
					return valueOf(difftime(args.checkdouble(1), args.checkdouble(2)));
				case EXECUTE:
					return valueOf(execute(args.optjstring(1, null)));
				case EXIT:
					exit(args.optint(1, 0));
					return NONE;
				case GETENV:
				{
					final String val = getenv(args.checkjstring(1));
					return val != null ? valueOf(val) : NIL;
				}
				case REMOVE:
					remove(args.checkjstring(1));
					return LuaValue.TRUE;
				case RENAME:
					rename(args.checkjstring(1), args.checkjstring(2));
					return LuaValue.TRUE;
				case SETLOCALE:
				{
					String s = setlocale(args.optjstring(1, null), args.optjstring(2, "all"));
					return s != null ? valueOf(s) : NIL;
				}
				case TIME:
					return valueOf(time(args.arg1().isnil() ? null : args.checktable(1)));
				case TMPNAME:
					return valueOf(tmpname());
			}
			return NONE;
		}
		catch(IOException e)
		{
			return varargsOf(NIL, valueOf(e.getMessage()));
		}
	}

	/**
	 * @return an approximation of the amount in seconds of CPU time used by
	 * the program.
	 */
	protected static double clock()
	{
		return (System.currentTimeMillis() - t0) / 1000.;
	}

	/**
	 * Returns the number of seconds from time t1 to time t2.
	 * In POSIX, Windows, and some other systems, this value is exactly t2-t1.
	 * @param t2
	 * @param t1
	 * @return diffeence in time values, in seconds
	 */
	protected static double difftime(double t2, double t1)
	{
		return t2 - t1;
	}

	/**
	 * If the time argument is present, this is the time to be formatted
	 * (see the os.time function for a description of this value).
	 * Otherwise, date formats the current time.
	 *
	 * If format starts with '!', then the date is formatted in Coordinated
	 * Universal Time. After this optional character, if format is the string
	 * "*t", then date returns a table with the following fields: year
	 * (four digits), month (1--12), day (1--31), hour (0--23), min (0--59),
	 * sec (0--61), wday (weekday, Sunday is 1), yday (day of the year),
	 * and isdst (daylight saving flag, a boolean).
	 *
	 * If format is not "*t", then date returns the date as a string,
	 * formatted according to the same rules as the C function strftime.
	 *
	 * When called without arguments, date returns a reasonable date and
	 * time representation that depends on the host system and on the
	 * current locale (that is, os.date() is equivalent to os.date("%c")).
	 *
	 * @param format
	 * @param time time since epoch, or -1 if not supplied
	 * @return a LString or a LTable containing date and time,
	 * formatted according to the given string format.
	 */
	protected static String date(String format, double time)
	{
		return new java.util.Date((long)(time * 1000)).toString();
	}

	/**
	 * This function is equivalent to the C function system.
	 * It passes command to be executed by an operating system shell.
	 * It returns a status code, which is system-dependent.
	 * If command is absent, then it returns nonzero if a shell
	 * is available and zero otherwise.
	 * @param command command to pass to the system
	 */
	protected static int execute(String command)
	{
		Runtime r = Runtime.getRuntime();
		try
		{
			final Process p = r.exec(command);
			try
			{
				p.waitFor();
				return p.exitValue();
			}
			finally
			{
				p.destroy();
			}
		}
		catch(IOException ioe)
		{
			return EXEC_IOEXCEPTION;
		}
		catch(InterruptedException e)
		{
			return EXEC_INTERRUPTED;
		}
		catch(Throwable t)
		{
			return EXEC_ERROR;
		}
	}

	/**
	 * Calls the C function exit, with an optional code, to terminate the host program.
	 * @param code
	 */
	protected static void exit(int code)
	{
		System.exit(code);
	}

	/**
	 * Returns the value of the process environment variable varname,
	 * or null if the variable is not defined.
	 * @param varname
	 * @return String value, or null if not defined
	 */
	protected static String getenv(String varname)
	{
		return System.getProperty(varname);
	}

	/**
	 * Deletes the file or directory with the given name.
	 * Directories must be empty to be removed.
	 * If this function fails, it throws and IOException
	 *
	 * @param filename
	 * @throws IOException if it fails
	 */
	protected static void remove(String filename) throws IOException
	{
		File f = new File(filename);
		if(!f.exists())
		    throw new IOException("No such file or directory");
		if(!f.delete())
		    throw new IOException("Failed to delete");
	}

	/**
	 * Renames file or directory named oldname to newname.
	 * If this function fails,it throws and IOException
	 *
	 * @param oldname old file name
	 * @param newname new file name
	 * @throws IOException if it fails
	 */
	protected static void rename(String oldname, String newname) throws IOException
	{
		File f = new File(oldname);
		if(!f.exists())
		    throw new IOException("No such file or directory");
		if(!f.renameTo(new File(newname)))
		    throw new IOException("Failed to delete");
	}

	/**
	 * Sets the current locale of the program. locale is a string specifying
	 * a locale; category is an optional string describing which category to change:
	 * "all", "collate", "ctype", "monetary", "numeric", or "time"; the default category
	 * is "all".
	 *
	 * If locale is the empty string, the current locale is set to an implementation-
	 * defined native locale. If locale is the string "C", the current locale is set
	 * to the standard C locale.
	 *
	 * When called with null as the first argument, this function only returns the
	 * name of the current locale for the given category.
	 *
	 * @param locale
	 * @param category
	 * @return the name of the new locale, or null if the request
	 * cannot be honored.
	 */
	protected static String setlocale(String locale, String category)
	{
		return "C";
	}

	/**
	 * Returns the current time when called without arguments,
	 * or a time representing the date and time specified by the given table.
	 * This table must have fields year, month, and day,
	 * and may have fields hour, min, sec, and isdst
	 * (for a description of these fields, see the os.date function).
	 * @param table
	 * @return long value for the time
	 */
	protected static long time(LuaTable table)
	{
		return System.currentTimeMillis();
	}

	/**
	 * Returns a string with a file name that can be used for a temporary file.
	 * The file must be explicitly opened before its use and explicitly removed
	 * when no longer needed.
	 *
	 * On some systems (POSIX), this function also creates a file with that name,
	 * to avoid security risks. (Someone else might create the file with wrong
	 * permissions in the time between getting the name and creating the file.)
	 * You still have to open the file to use it and to remove it (even if you
	 * do not use it).
	 *
	 * @return String filename to use
	 */
	protected static String tmpname()
	{
		try
		{
			File f = File.createTempFile(TMP_PREFIX, TMP_SUFFIX);
			return f.getName();
		}
		catch(IOException ioe)
		{
			return TMP_PREFIX + tmpnames.getAndIncrement() + TMP_SUFFIX;
		}
	}
}
