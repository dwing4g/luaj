import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.luaj.vm2.Lua;
import org.luaj.vm2.Print;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.compiler.DumpState;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.JsePlatform;

/**
 * Compiler for lua files to lua bytecode.
 */
public final class luac
{
	private static boolean list         = false;
	private static String  output       = "luac.out";
	private static boolean parseonly    = false;
	private static boolean stripdebug   = false;
	private static boolean littleendian = false;
	private static int     numberformat = DumpState.NUMBER_FORMAT_DEFAULT;
	private static boolean versioninfo  = false;
	private static boolean processing   = true;

	private static void usageExit()
	{
		//@formatter:off
		String usage =
			"usage: java -cp luaj-jse.jar luac [options] [filenames].\n" +
			"Available options are:\n" +
			"  -        process stdin\n" +
			"  -l       list\n" +
			"  -o name  output to file 'name' (default is \"luac.out\")\n" +
			"  -p       parse only\n" +
			"  -s       strip debug information\n" +
			"  -e       little endian format for numbers\n" +
			"  -i<n>    number format 'n', (n=0,1 or 4, default="+DumpState.NUMBER_FORMAT_DEFAULT+")\n" +
			"  -v       show version information\n" +
			"  --       stop handling options\n";
		//@formatter:on
		System.out.println(usage);
		System.exit(-1);
	}

	private static void processScript(InputStream script, String chunkname, OutputStream out) throws IOException
	{
		try
		{
			// create the chunk
			Prototype chunk = LuaC.compile(script, chunkname);

			// list the chunk
			if(list)
			    Print.printCode(System.out, chunk);

			// write out the chunk
			if(!parseonly)
			{
				DumpState.dump(chunk, out, stripdebug, numberformat, littleendian);
			}
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
		}
		finally
		{
			script.close();
		}
	}

	public static void main(String[] args)
	{
		try
		{
			// get stateful args
			for(int i = 0; i < args.length; i++)
			{
				if(!processing || !args[i].startsWith("-"))
				{
					// input file - defer to next stage
				}
				else if(args[i].length() <= 1)
				{
					// input file - defer to next stage
				}
				else
				{
					switch(args[i].charAt(1))
					{
						case 'l':
							list = true;
							break;
						case 'o':
							if(++i >= args.length)
							    usageExit();
							output = args[i];
							break;
						case 'p':
							parseonly = true;
							break;
						case 's':
							stripdebug = true;
							break;
						case 'e':
							littleendian = true;
							break;
						case 'i':
							if(args[i].length() <= 2)
							    usageExit();
							numberformat = Integer.parseInt(args[i].substring(2));
							break;
						case 'v':
							versioninfo = true;
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
			    System.out.println(Lua._VERSION + "Copyright (C) 2009 luaj.org");

			OutputStream fos = new FileOutputStream(output);

			// process input files
			try
			{
				JsePlatform.standardGlobals();
				processing = true;
				for(int i = 0; i < args.length; i++)
				{
					if(!processing || !args[i].startsWith("-"))
					{
						String chunkname = args[i].substring(0, args[i].length() - 4);
						FileInputStream fis = new FileInputStream(args[i]);
						try
						{
							processScript(fis, chunkname, fos);
						}
						finally
						{
							fis.close();
						}
					}
					else if(args[i].length() <= 1)
					{
						processScript(System.in, "=stdin", fos);
					}
					else
					{
						switch(args[i].charAt(1))
						{
							case 'o':
								++i;
								break;
							case '-':
								processing = false;
								break;
						}
					}
				}
			}
			finally
			{
				fos.close();
			}
		}
		catch(IOException ioe)
		{
			System.err.println(ioe.toString());
			System.exit(-2);
		}
	}
}
