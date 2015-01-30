import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * lua command for use in java se environments.
 */
public final class lua
{
	private static LuaValue _G;

	private static void usageExit()
	{
		//@formatter:off
		String usage =
			"usage: java -cp luaj-jse.jar lua [options] [script [args]].\n" +
			"Available options are:\n" +
			"  -e stat  execute string 'stat'\n" +
			"  -l name  require library 'name'\n" +
			"  -i       enter interactive mode after executing 'script'\n" +
			"  -v       show version information\n" +
			"  -n      	nodebug - do not load debug library by default\n" +
			"  --       stop handling options\n" +
			"  -        execute stdin and stop handling options";
		//@formatter:on
		System.out.println(usage);
		System.exit(-1);
	}

	private static void loadLibrary(String libname) throws IOException
	{
		LuaValue slibname = LuaValue.valueOf(libname);
		try
		{
			// load via plain require
			_G.get("require").call(slibname);
		}
		catch(Exception e)
		{
			try
			{
				// load as java class
				LuaValue v = (LuaValue)Class.forName(libname).newInstance();
				v.setfenv(_G);
				v.call(slibname, _G);
			}
			catch(Exception f)
			{
				throw new IOException("loadLibrary(" + libname + ") failed: " + e + "," + f);
			}
		}
	}

	private static void processScript(InputStream script, String chunkname, String[] args, int firstarg)
	{
		try
		{
			LuaFunction c;
			try
			{
				c = LoadState.load(script, chunkname, _G);
			}
			finally
			{
				script.close();
			}
			Varargs scriptargs = (args != null ? setGlobalArg(args, firstarg) : LuaValue.NONE);
			c.invoke(scriptargs);
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
		}
	}

	private static Varargs setGlobalArg(String[] args, int i)
	{
		LuaTable arg = LuaValue.tableOf();
		for(int j = 0; j < args.length; j++)
			arg.set(j - i, LuaValue.valueOf(args[j]));
		_G.set("arg", arg);
		return _G.get("unpack").invoke(arg);
	}

	private static void interactiveMode() throws IOException
	{
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		while(true)
		{
			System.out.print("> ");
			System.out.flush();
			String line = reader.readLine();
			if(line == null)
			    return;
			processScript(new ByteArrayInputStream(line.getBytes()), "=stdin", null, 0);
		}
	}

	public static void main(String[] args)
	{
		boolean interactive = (args.length == 0);
		boolean versioninfo = false;
		boolean processing = true;
		boolean nodebug = false;
		ArrayList<String> libs = null;
		try
		{
			// stateful argument processing
			for(int i = 0; i < args.length; i++)
			{
				if(!processing || !args[i].startsWith("-"))
				{
					// input file - defer to last stage
					break;
				}
				else if(args[i].length() <= 1)
				{
					// input file - defer to last stage
					break;
				}
				else
				{
					switch(args[i].charAt(1))
					{
						case 'e':
							if(++i >= args.length)
							    usageExit();
							// input script - defer to last stage
							break;
						case 'l':
							if(++i >= args.length)
							    usageExit();
							libs = libs != null ? libs : new ArrayList<String>();
							libs.add(args[i]);
							break;
						case 'i':
							interactive = true;
							break;
						case 'v':
							versioninfo = true;
							break;
						case 'n':
							nodebug = true;
							break;
						case '-':
							if(args[i].length() > 2)
							    usageExit();
							processing = false;
							break;
						default:
							usageExit();
							break;
					}
				}
			}

			if(versioninfo)
			    System.out.println(Lua._VERSION + "Copyright (c) 2009 Luaj.org.org");

			// new lua state
			_G = nodebug ? JsePlatform.standardGlobals() : JsePlatform.debugGlobals();
			if(libs != null)
			{
				for(int i = 0, n = libs.size(); i < n; i++)
					loadLibrary(libs.get(i));
			}

			// input script processing
			processing = true;
			for(int i = 0; i < args.length; i++)
			{
				if(!processing || !args[i].startsWith("-"))
				{
					processScript(new FileInputStream(args[i]), args[i], args, i);
					break;
				}
				else if("-".equals(args[i]))
				{
					processScript(System.in, "=stdin", args, i);
					break;
				}
				else
				{
					switch(args[i].charAt(1))
					{
						case 'l':
							++i;
							break;
						case 'e':
							++i;
							processScript(new ByteArrayInputStream(args[i].getBytes()), "string", args, i);
							break;
						case '-':
							processing = false;
							break;
					}
				}
			}

			if(interactive)
			    interactiveMode();
		}
		catch(IOException ioe)
		{
			System.err.println(ioe.toString());
			System.exit(-2);
		}
	}
}
