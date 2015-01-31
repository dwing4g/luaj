package org.luaj.vm2.lib.jse;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.IoLib;
import org.luaj.vm2.lib.LibFunction;

/**
 * Subclass of {@link IoLib} and therefore {@link LibFunction} which implements the lua standard {@code io}
 * library for the JSE platform.
 * <p>
 * It uses RandomAccessFile to implement seek on files.
 * <p>
 * Typically, this library is included as part of a call to
 * {@link JsePlatform#standardGlobals()}
 * <p>
 * To instantiate and use it directly,
 * link it into your globals table via {@link LuaValue#load(LuaValue)} using code such as:
 * <pre> {@code
 * LuaTable _G = new LuaTable();
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
 * @see JsePlatform
 * @see IoLib
 * @see <a href="http://www.lua.org/manual/5.1/manual.html#5.7">http://www.lua.org/manual/5.1/manual.html#5.7</a>
 */
public class JseIoLib extends IoLib
{
	public JseIoLib()
	{
		super();
	}

	@Override
	protected File wrapStdin() throws IOException
	{
		return new FileImpl(System.in);
	}

	@Override
	protected File wrapStdout() throws IOException
	{
		return new FileImpl(System.out);
	}

	@SuppressWarnings("resource")
	@Override
	protected File openFile(String filename, boolean readMode, boolean appendMode, boolean updateMode, boolean binaryMode) throws IOException
	{
		RandomAccessFile f = new RandomAccessFile(filename, readMode ? "r" : "rw");
		if(appendMode)
			f.seek(f.length());
		else
		{
			if(!readMode)
			    f.setLength(0);
		}
		return new FileImpl(f);
	}

	@Override
	protected File openProgram(String prog, String mode) throws IOException
	{
		final Process p = Runtime.getRuntime().exec(prog);
		return "w".equals(mode) ?
		        new FileImpl(p.getOutputStream()) :
		        new FileImpl(p.getInputStream());
	}

	@SuppressWarnings("resource")
	@Override
	protected File tmpFile() throws IOException
	{
		java.io.File f = java.io.File.createTempFile(".luaj", "bin");
		f.deleteOnExit();
		return new FileImpl(new RandomAccessFile(f, "rw"));
	}

	private static void notimplemented()
	{
		throw new LuaError("not implemented");
	}

	private final class FileImpl extends File
	{
		private final RandomAccessFile file;
		private final InputStream      is;
		private final OutputStream     os;
		private boolean                closed   = false;
		private boolean                nobuffer = false;

		private FileImpl(RandomAccessFile file, InputStream is, OutputStream os)
		{
			this.file = file;
			this.is = is != null ? is.markSupported() ? is : new BufferedInputStream(is) : null;
			this.os = os;
		}

		private FileImpl(RandomAccessFile f)
		{
			this(f, null, null);
		}

		private FileImpl(InputStream i)
		{
			this(null, i, null);
		}

		private FileImpl(OutputStream o)
		{
			this(null, null, o);
		}

		@Override
		public String tojstring()
		{
			return "file (" + this.hashCode() + ")";
		}

		@Override
		public boolean isstdfile()
		{
			return file == null;
		}

		@Override
		public void close() throws IOException
		{
			closed = true;
			if(file != null)
			{
				file.close();
			}
		}

		@Override
		public void flush() throws IOException
		{
			if(os != null)
			    os.flush();
		}

		@Override
		public void write(LuaString s) throws IOException
		{
			if(os != null)
				os.write(s.m_bytes, s.m_offset, s.m_length);
			else if(file != null)
				file.write(s.m_bytes, s.m_offset, s.m_length);
			else
				notimplemented();
			if(nobuffer)
			    flush();
		}

		@Override
		public boolean isclosed()
		{
			return closed;
		}

		@Override
		public int seek(String option, int pos) throws IOException
		{
			if(file != null)
			{
				if("set".equals(option))
				{
					file.seek(pos);
				}
				else if("end".equals(option))
				{
					file.seek(file.length() + pos);
				}
				else
				{
					file.seek(file.getFilePointer() + pos);
				}
				return (int)file.getFilePointer();
			}
			notimplemented();
			return 0;
		}

		@Override
		public void setvbuf(String mode, int size)
		{
			nobuffer = "no".equals(mode);
		}

		// get length remaining to read
		@Override
		public int remaining() throws IOException
		{
			return file != null ? (int)(file.length() - file.getFilePointer()) : -1;
		}

		// peek ahead one character
		@Override
		public int peek() throws IOException
		{
			if(is != null)
			{
				is.mark(1);
				int c = is.read();
				is.reset();
				return c;
			}
			else if(file != null)
			{
				long fp = file.getFilePointer();
				int c = file.read();
				file.seek(fp);
				return c;
			}
			notimplemented();
			return 0;
		}

		// return char if read, -1 if eof, throw IOException on other exception
		@Override
		public int read() throws IOException
		{
			if(is != null)
				return is.read();
			else if(file != null)
			{
				return file.read();
			}
			notimplemented();
			return 0;
		}

		// return number of bytes read if positive, -1 if eof, throws IOException
		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException
		{
			if(file != null)
			{
				return file.read(bytes, offset, length);
			}
			else if(is != null)
			{
				return is.read(bytes, offset, length);
			}
			else
			{
				notimplemented();
			}
			return length;
		}
	}
}
