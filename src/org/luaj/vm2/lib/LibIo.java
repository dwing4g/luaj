package org.luaj.vm2.lib;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Abstract base class extending {@link LibFunction} which implements the
 * core of the lua standard {@code io} library.
 * <p>
 * It contains the implementation of the io library support that is common to the JSE platforms.
 * In practice on of the concrete IOLib subclasses is chosen:
 * {@link IoLib} for the JSE platform.
 * <p>
 * The JSE implementation conforms almost completely to the C-based lua library,
 * while the JME implementation follows closely except in the area of random-access files,
 * which are difficult to support properly on JME.
 * <p>
 * To instantiate and use it directly,
 * link it into your globals table via {@link LuaValue#load(LuaValue)} using code such as:
 * <pre> {@code
 * LuaTable _G = new LuaTable();
 * _G.load(new JseIoLib());
 * LuaThread.setGlobals(_G);
 * _G.load(new JseBaseLib());
 * _G.load(new PackageLib());
 * _G.load(new JseIoLib());
 * _G.get("io").get("write").call(LuaValue.valueOf("hello, world\n"));
 * } </pre>
 * Doing so will ensure the library is properly initialized
 * and loaded into the globals table.
 * <p>
 * This has been implemented to match as closely as possible the behavior in the corresponding library in C.
 * @see LibFunction
 * @see JseIoLib
 * @see <a href="http://www.lua.org/manual/5.1/manual.html#5.7">http://www.lua.org/manual/5.1/manual.html#5.7</a>
 */
public final class LibIo extends LibFunction1
{
	abstract protected class LuaFile extends LuaValue
	{
		public abstract void write(LuaString string) throws IOException;

		public abstract void flush() throws IOException;

		public abstract boolean isstdfile();

		public abstract void close() throws IOException;

		public abstract boolean isclosed();

		// returns new position
		public abstract int seek(String option, int bytecount) throws IOException;

		public abstract void setvbuf(String mode, int size);

		// get length remaining to read
		public abstract int remaining() throws IOException;

		// peek ahead one character
		public abstract int peek() throws IOException, EOFException;

		// return char if read, -1 if eof, throw IOException on other exception
		public abstract int read() throws IOException, EOFException;

		// return number of bytes read if positive, false if eof, throw IOException on other exception
		public abstract int read(byte[] bytes, int offset, int length) throws IOException;

		// delegate method access to file methods table
		@Override
		public LuaValue get(LuaValue key)
		{
			return filemethods.get(key);
		}

		// essentially a userdata instance
		@Override
		public int type()
		{
			return LuaValue.TUSERDATA;
		}

		@Override
		public String typename()
		{
			return "userdata";
		}

		// displays as "file" type
		@Override
		public String tojstring()
		{
			return "file: " + Integer.toHexString(hashCode());
		}
	}

	private LuaFile               infile;
	private LuaFile               outfile;
	private LuaFile               errfile;

	private static final LuaValue STDIN        = valueOf("stdin");
	private static final LuaValue STDOUT       = valueOf("stdout");
	private static final LuaValue STDERR       = valueOf("stderr");
	private static final LuaValue FILE         = valueOf("file");
	private static final LuaValue CLOSED_FILE  = valueOf("closed file");

	private static final int      IO_CLOSE     = 0;
	private static final int      IO_FLUSH     = 1;
	private static final int      IO_INPUT     = 2;
	private static final int      IO_LINES     = 3;
	private static final int      IO_OPEN      = 4;
	private static final int      IO_OUTPUT    = 5;
	private static final int      IO_POPEN     = 6;
	private static final int      IO_READ      = 7;
	private static final int      IO_TMPFILE   = 8;
	private static final int      IO_TYPE      = 9;
	private static final int      IO_WRITE     = 10;

	private static final int      FILE_CLOSE   = 11;
	private static final int      FILE_FLUSH   = 12;
	private static final int      FILE_LINES   = 13;
	private static final int      FILE_READ    = 14;
	private static final int      FILE_SEEK    = 15;
	private static final int      FILE_SETVBUF = 16;
	private static final int      FILE_WRITE   = 17;

	private static final int      IO_INDEX     = 18;
	private static final int      LINES_ITER   = 19;

	private static final String[] IO_NAMES     = {
	                                           "close",
	                                           "flush",
	                                           "input",
	                                           "lines",
	                                           "open",
	                                           "output",
	                                           "popen",
	                                           "read",
	                                           "tmpfile",
	                                           "type",
	                                           "write",
	                                           };

	private static final String[] FILE_NAMES   = {
	                                           "close",
	                                           "flush",
	                                           "lines",
	                                           "read",
	                                           "seek",
	                                           "setvbuf",
	                                           "write",
	                                           };

	LuaTable                      filemethods;

	@Override
	public LuaValue call(LuaValue arg)
	{
		// io lib functions
		LuaTable t = new LuaTable();
		bind(t, IoLibV.class, IO_NAMES);

		// create file methods table
		filemethods = new LuaTable();
		bind(filemethods, IoLibV.class, FILE_NAMES, FILE_CLOSE);

		// set up file metatable
		LuaTable mt = new LuaTable();
		bind(mt, IoLibV.class, new String[] { "__index" }, IO_INDEX);
		t.setmetatable(mt);

		// all functions link to library instance
		setLibInstance(t);
		setLibInstance(filemethods);
		setLibInstance(mt);

		// return the table
		_env.set("io", t);
		LibPackage.instance.LOADED.set("io", t);
		return t;
	}

	private void setLibInstance(LuaTable t)
	{
		List<LuaValue> k = t.keys();
		for(int i = 0, n = k.size(); i < n; i++)
			((IoLibV)t.get(k.get(i)))._iolib = this;
	}

	static final class IoLibV extends LibFunctionV
	{
		public LibIo _iolib;

		public IoLibV()
		{
		}

		public IoLibV(LuaValue env, String name, int opcode, LibIo iolib)
		{
			_env = env;
			_name = name;
			_opcode = opcode;
			_iolib = iolib;
		}

		@Override
		public Varargs invoke(Varargs args)
		{
			try
			{
				switch(_opcode)
				{
					case IO_FLUSH:
						return _iolib._io_flush();
					case IO_TMPFILE:
						return _iolib._io_tmpfile();
					case IO_CLOSE:
						return _iolib._io_close(args.arg1());
					case IO_INPUT:
						return _iolib._io_input(args.arg1());
					case IO_OUTPUT:
						return _iolib._io_output(args.arg1());
					case IO_TYPE:
						return LibIo._io_type(args.arg1());
					case IO_POPEN:
						return _iolib._io_popen(args.checkjstring(1), args.optjstring(2, "r"));
					case IO_OPEN:
						return _iolib._io_open(args.checkjstring(1), args.optjstring(2, "r"));
					case IO_LINES:
						return _iolib._io_lines(args.isvalue(1) ? args.checkjstring(1) : null);
					case IO_READ:
						return _iolib._io_read(args);
					case IO_WRITE:
						return _iolib._io_write(args);
					case FILE_CLOSE:
						return LibIo._file_close(args.arg1());
					case FILE_FLUSH:
						return LibIo._file_flush(args.arg1());
					case FILE_SETVBUF:
						return LibIo._file_setvbuf(args.arg1(), args.checkjstring(2), args.optint(3, 1024));
					case FILE_LINES:
						return _iolib._file_lines(args.arg1());
					case FILE_READ:
						return LibIo._file_read(args.arg1(), args.subargs(2));
					case FILE_SEEK:
						return LibIo._file_seek(args.arg1(), args.optjstring(2, "cur"), args.optint(3, 0));
					case FILE_WRITE:
						return LibIo._file_write(args.arg1(), args.subargs(2));
					case IO_INDEX:
						return _iolib._io_index(args.arg(2));
					case LINES_ITER:
						return LibIo._lines_iter(_env);
				}
			}
			catch(IOException ioe)
			{
				return errorresult(ioe);
			}
			return NONE;
		}
	}

	private LuaFile input()
	{
		return infile != null ? infile : (infile = ioopenfile("-", "r"));
	}

	//	io.flush() -> bool
	public Varargs _io_flush() throws IOException
	{
		checkopen(output());
		outfile.flush();
		return LuaValue.TRUE;
	}

	//	io.tmpfile() -> file
	public Varargs _io_tmpfile() throws IOException
	{
		return tmpFile();
	}

	//	io.close([file]) -> void
	public Varargs _io_close(LuaValue file) throws IOException
	{
		LuaFile f = file.isnil() ? output() : checkfile(file);
		checkopen(f);
		return ioclose(f);
	}

	//	io.input([file]) -> file
	public Varargs _io_input(LuaValue file)
	{
		infile = file.isnil() ? input() :
		        file.isstring() ? ioopenfile(file.checkjstring(), "r") :
		                checkfile(file);
		return infile;
	}

	// io.output(filename) -> file
	public Varargs _io_output(LuaValue filename)
	{
		outfile = filename.isnil() ? output() :
		        filename.isstring() ? ioopenfile(filename.checkjstring(), "w") :
		                checkfile(filename);
		return outfile;
	}

	//	io.type(obj) -> "file" | "closed file" | nil
	public static Varargs _io_type(LuaValue obj)
	{
		LuaFile f = optfile(obj);
		return f != null ? f.isclosed() ? CLOSED_FILE : FILE : NIL;
	}

	// io.popen(prog, [mode]) -> file
	public Varargs _io_popen(String prog, String mode) throws IOException
	{
		return openProgram(prog, mode);
	}

	//	io.open(filename, [mode]) -> file | nil,err
	public Varargs _io_open(String filename, String mode) throws IOException
	{
		return rawopenfile(filename, mode);
	}

	//	io.lines(filename) -> iterator
	public Varargs _io_lines(String filename)
	{
		infile = filename == null ? input() : ioopenfile(filename, "r");
		checkopen(infile);
		return lines(infile);
	}

	//	io.read(...) -> (...)
	public Varargs _io_read(Varargs args) throws IOException
	{
		checkopen(input());
		return ioread(infile, args);
	}

	//	io.write(...) -> void
	public Varargs _io_write(Varargs args) throws IOException
	{
		checkopen(output());
		return iowrite(outfile, args);
	}

	// file:close() -> void
	public static Varargs _file_close(LuaValue file) throws IOException
	{
		return ioclose(checkfile(file));
	}

	// file:flush() -> void
	public static Varargs _file_flush(LuaValue file) throws IOException
	{
		checkfile(file).flush();
		return LuaValue.TRUE;
	}

	// file:setvbuf(mode,[size]) -> void
	public static Varargs _file_setvbuf(LuaValue file, String mode, int size)
	{
		checkfile(file).setvbuf(mode, size);
		return LuaValue.TRUE;
	}

	// file:lines() -> iterator
	public Varargs _file_lines(LuaValue file)
	{
		return lines(checkfile(file));
	}

	//	file:read(...) -> (...)
	public static Varargs _file_read(LuaValue file, Varargs subargs) throws IOException
	{
		return ioread(checkfile(file), subargs);
	}

	//  file:seek([whence][,offset]) -> pos | nil,error
	public static Varargs _file_seek(LuaValue file, String whence, int offset) throws IOException
	{
		return valueOf(checkfile(file).seek(whence, offset));
	}

	//	file:write(...) -> void
	public static Varargs _file_write(LuaValue file, Varargs subargs) throws IOException
	{
		return iowrite(checkfile(file), subargs);
	}

	// __index, returns a field
	public Varargs _io_index(LuaValue v)
	{
		return v.equals(STDOUT) ? output() :
		        v.equals(STDIN) ? input() :
		                v.equals(STDERR) ? errput() : NIL;
	}

	//	lines iterator(s,var) -> var'
	public static Varargs _lines_iter(LuaValue file) throws IOException
	{
		return freadline(checkfile(file));
	}

	private LuaFile output()
	{
		return outfile != null ? outfile : (outfile = ioopenfile("-", "w"));
	}

	private LuaFile errput()
	{
		return errfile != null ? errfile : (errfile = ioopenfile("-", "w"));
	}

	private LuaFile ioopenfile(String filename, String mode)
	{
		try
		{
			return rawopenfile(filename, mode);
		}
		catch(Exception e)
		{
			error("io error: " + e.getMessage());
			return null;
		}
	}

	private static Varargs ioclose(LuaFile f) throws IOException
	{
		if(f.isstdfile())
		    return errorresult("cannot close standard file");
		f.close();
		return successresult();
	}

	private static Varargs successresult()
	{
		return LuaValue.TRUE;
	}

	private static Varargs errorresult(Exception ioe)
	{
		String s = ioe.getMessage();
		return errorresult("io error: " + (s != null ? s : ioe.toString()));
	}

	private static Varargs errorresult(String errortext)
	{
		return varargsOf(NIL, valueOf(errortext));
	}

	private Varargs lines(final LuaFile f)
	{
		try
		{
			return new IoLibV(f, "lnext", LINES_ITER, this);
		}
		catch(Exception e)
		{
			return error("lines: " + e);
		}
	}

	private static Varargs iowrite(LuaFile f, Varargs args) throws IOException
	{
		for(int i = 1, n = args.narg(); i <= n; i++)
			f.write(args.checkstring(i));
		return LuaValue.TRUE;
	}

	private static Varargs ioread(LuaFile f, Varargs args) throws IOException
	{
		int i, n = args.narg();
		LuaValue[] v = new LuaValue[n];
		LuaValue ai, vi;
		LuaString fmt;
		for(i = 0; i < n;)
		{
			item: switch((ai = args.arg(i + 1)).type())
			{
				case LuaValue.TNUMBER:
					vi = freadbytes(f, ai.toint());
					break item;
				case LuaValue.TSTRING:
					fmt = ai.checkstring();
					if(fmt._length == 2 && fmt._bytes[fmt._offset] == '*')
					{
						switch(fmt._bytes[fmt._offset + 1])
						{
							case 'n':
								vi = freadnumber(f);
								break item;
							case 'l':
								vi = freadline(f);
								break item;
							case 'a':
								vi = freadall(f);
								break item;
						}
					}
					//$FALL-THROUGH$
				default:
					return argerror(i + 1, "(invalid format)");
			}
			if((v[i++] = vi).isnil())
			    break;
		}
		return i == 0 ? NIL : varargsOf(v, 0, i);
	}

	private static LuaFile checkfile(LuaValue val)
	{
		LuaFile f = optfile(val);
		if(f == null)
		    argerror(1, "file");
		checkopen(f);
		return f;
	}

	private static LuaFile optfile(LuaValue val)
	{
		return (val instanceof LuaFile) ? (LuaFile)val : null;
	}

	private static LuaFile checkopen(LuaFile file)
	{
		if(file.isclosed())
		    error("attempt to use a closed file");
		return file;
	}

	private LuaFile rawopenfile(String filename, String mode) throws IOException
	{
		boolean isstdfile = "-".equals(filename);
		boolean isreadmode = mode.startsWith("r");
		if(isstdfile)
		    return isreadmode ? wrapStdin() : wrapStdout();
		boolean isappend = mode.startsWith("a");
		boolean isupdate = mode.indexOf("+") > 0;
		boolean isbinary = mode.endsWith("b");
		return openFile(filename, isreadmode, isappend, isupdate, isbinary);
	}

	// ------------- file reading utilitied ------------------

	public static LuaValue freadbytes(LuaFile f, int count) throws IOException
	{
		byte[] b = new byte[count];
		int r;
		if((r = f.read(b, 0, b.length)) < 0)
		    return NIL;
		return LuaString.valueOf(b, 0, r);
	}

	public static LuaValue freaduntil(LuaFile f, boolean lineonly) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int c;
		try
		{
			if(lineonly)
			{
				loop: while((c = f.read()) > 0)
				{
					switch(c)
					{
						case '\r':
							break;
						case '\n':
							break loop;
						default:
							baos.write(c);
							break;
					}
				}
			}
			else
			{
				while((c = f.read()) > 0)
					baos.write(c);
			}
		}
		catch(EOFException e)
		{
			c = -1;
		}
		return (c < 0 && baos.size() == 0) ?
		        (LuaValue)NIL :
		        (LuaValue)LuaString.valueOf(baos.toByteArray());
	}

	public static LuaValue freadline(LuaFile f) throws IOException
	{
		return freaduntil(f, true);
	}

	public static LuaValue freadall(LuaFile f) throws IOException
	{
		int n = f.remaining();
		if(n >= 0)
		    return freadbytes(f, n);
		return freaduntil(f, false);
	}

	public static LuaValue freadnumber(LuaFile f) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		freadchars(f, " \t\r\n", null);
		freadchars(f, "-+", baos);
		//freadchars(f,"0",baos);
		//freadchars(f,"xX",baos);
		freadchars(f, "0123456789", baos);
		freadchars(f, ".", baos);
		freadchars(f, "0123456789", baos);
		//freadchars(f,"eEfFgG",baos);
		// freadchars(f,"+-",baos);
		//freadchars(f,"0123456789",baos);
		String s = baos.toString();
		return s.length() > 0 ? valueOf(Double.parseDouble(s)) : NIL;
	}

	private static void freadchars(LuaFile f, String chars, ByteArrayOutputStream baos) throws IOException
	{
		for(;;)
		{
			int c = f.peek();
			if(chars.indexOf(c) < 0)
			    return;
			f.read();
			if(baos != null)
			    baos.write(c);
		}
	}

	/**
	 * Wrap the standard input.
	 * @return File
	 * @throws IOException
	 */
	protected LuaFile wrapStdin() throws IOException
	{
		return new LuaFileImpl(System.in);
	}

	/**
	 * Wrap the standard output.
	 * @return File
	 * @throws IOException
	 */
	protected LuaFile wrapStdout() throws IOException
	{
		return new LuaFileImpl(System.out);
	}

	/**
	 * Open a file in a particular mode.
	 * @param filename
	 * @param readMode true if opening in read mode
	 * @param appendMode true if opening in append mode
	 * @param updateMode true if opening in update mode
	 * @param binaryMode true if opening in binary mode
	 * @return File object if successful
	 * @throws IOException if could not be opened
	 */
	@SuppressWarnings("resource")
	protected LuaFile openFile(String filename, boolean readMode, boolean appendMode, boolean updateMode, boolean binaryMode) throws IOException
	{
		RandomAccessFile f = new RandomAccessFile(filename, readMode ? "r" : "rw");
		if(appendMode)
			f.seek(f.length());
		else if(!readMode)
		    f.setLength(0);
		return new LuaFileImpl(f);
	}

	/**
	 * Start a new process and return a file for input or output
	 * @param prog the program to execute
	 * @param mode "r" to read, "w" to write
	 * @return File to read to or write from
	 * @throws IOException if an i/o exception occurs
	 */
	protected LuaFile openProgram(String prog, String mode) throws IOException
	{
		final Process p = Runtime.getRuntime().exec(prog);
		return "w".equals(mode) ?
		        new LuaFileImpl(p.getOutputStream()) :
		        new LuaFileImpl(p.getInputStream());
	}

	/**
	 * Open a temporary file.
	 * @return File object if successful
	 * @throws IOException if could not be opened
	 */
	@SuppressWarnings("resource")
	protected LuaFile tmpFile() throws IOException
	{
		File f = File.createTempFile(".luaj", "bin");
		f.deleteOnExit();
		return new LuaFileImpl(new RandomAccessFile(f, "rw"));
	}

	private static void notimplemented()
	{
		throw new LuaError("not implemented");
	}

	private final class LuaFileImpl extends LuaFile
	{
		private final RandomAccessFile _file;
		private final InputStream      _is;
		private final OutputStream     _os;
		private boolean                _closed;
		private boolean                _nobuffer;

		private LuaFileImpl(RandomAccessFile file, InputStream is, OutputStream os)
		{
			_file = file;
			_is = is != null ? is.markSupported() ? is : new BufferedInputStream(is) : null;
			_os = os;
		}

		private LuaFileImpl(RandomAccessFile f)
		{
			this(f, null, null);
		}

		private LuaFileImpl(InputStream i)
		{
			this(null, i, null);
		}

		private LuaFileImpl(OutputStream o)
		{
			this(null, null, o);
		}

		@Override
		public String tojstring()
		{
			return "file (" + hashCode() + ")";
		}

		@Override
		public boolean isstdfile()
		{
			return _file == null;
		}

		@Override
		public void close() throws IOException
		{
			_closed = true;
			if(_file != null)
			{
				_file.close();
			}
		}

		@Override
		public void flush() throws IOException
		{
			if(_os != null)
			    _os.flush();
		}

		@Override
		public void write(LuaString s) throws IOException
		{
			if(_os != null)
				_os.write(s._bytes, s._offset, s._length);
			else if(_file != null)
				_file.write(s._bytes, s._offset, s._length);
			else
				notimplemented();
			if(_nobuffer)
			    flush();
		}

		@Override
		public boolean isclosed()
		{
			return _closed;
		}

		@Override
		public int seek(String option, int pos) throws IOException
		{
			if(_file != null)
			{
				if("set".equals(option))
				{
					_file.seek(pos);
				}
				else if("end".equals(option))
				{
					_file.seek(_file.length() + pos);
				}
				else
				{
					_file.seek(_file.getFilePointer() + pos);
				}
				return (int)_file.getFilePointer();
			}
			notimplemented();
			return 0;
		}

		@Override
		public void setvbuf(String mode, int size)
		{
			_nobuffer = "no".equals(mode);
		}

		// get length remaining to read
		@Override
		public int remaining() throws IOException
		{
			return _file != null ? (int)(_file.length() - _file.getFilePointer()) : -1;
		}

		// peek ahead one character
		@Override
		public int peek() throws IOException
		{
			if(_is != null)
			{
				_is.mark(1);
				int c = _is.read();
				_is.reset();
				return c;
			}
			else if(_file != null)
			{
				long fp = _file.getFilePointer();
				int c = _file.read();
				_file.seek(fp);
				return c;
			}
			notimplemented();
			return 0;
		}

		// return char if read, -1 if eof, throw IOException on other exception
		@Override
		public int read() throws IOException
		{
			if(_is != null)
				return _is.read();
			else if(_file != null)
			{
				return _file.read();
			}
			notimplemented();
			return 0;
		}

		// return number of bytes read if positive, -1 if eof, throws IOException
		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException
		{
			if(_file != null)
			{
				return _file.read(bytes, offset, length);
			}
			else if(_is != null)
			{
				return _is.read(bytes, offset, length);
			}
			else
			{
				notimplemented();
			}
			return length;
		}
	}
}
