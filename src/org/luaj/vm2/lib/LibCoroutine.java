package org.luaj.vm2.lib;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Subclass of {@link LibFunction} which implements the lua standard {@code coroutine}
 * library.
 * <p>
 * The coroutine library in luaj has the same behavior as the
 * coroutine library in C, but is implemented using Java Threads to maintain
 * the call state between invocations.  Therefore it can be yielded from anywhere,
 * similar to the "Coco" yield-from-anywhere patch available for C-based lua.
 * However, coroutines that are yielded but never resumed to complete their execution
 * may not be collected by the garbage collector.
 * <p>
 * Typically, this library is included as part of a call to {@link JsePlatform#standardGlobals()}
 * <p>
 * To instantiate and use it directly,
 * link it into your globals table via {@link LuaValue#load(LuaValue)} using code such as:
 * <pre> {@code
 * LuaTable _G = new LuaTable();
 * _G.load(new CoroutineLib());
 * } </pre>
 * Doing so will ensure the library is properly initialized
 * and loaded into the globals table.
 * <p>
 * @see LibFunction
 * @see <a href="http://www.lua.org/manual/5.1/manual.html#5.2">http://www.lua.org/manual/5.1/manual.html#5.2</a>
 */
public class LibCoroutine extends LibFunctionV
{
	private static final int INIT    = 0;
	private static final int CREATE  = 1;
	private static final int RESUME  = 2;
	private static final int RUNNING = 3;
	private static final int STATUS  = 4;
	private static final int YIELD   = 5;
	private static final int WRAP    = 6;
	private static final int WRAPPED = 7;

	public LibCoroutine()
	{
	}

	private LuaTable init()
	{
		LuaTable t = new LuaTable();
		bind(t, LibCoroutine.class, new String[] {
		        "create", "resume", "running", "status", "yield", "wrap" },
		        CREATE);
		env.set("coroutine", t);
		LibPackage.instance.LOADED.set("coroutine", t);
		return t;
	}

	@Override
	public Varargs invoke(Varargs args)
	{
		switch(_opcode)
		{
			case INIT:
			{
				return init();
			}
			case CREATE:
			{
				final LuaValue func = args.checkfunction(1);
				return new LuaThread(func, LuaThread.getGlobals());
			}
			case RESUME:
			{
				final LuaThread t = args.checkthread(1);
				return t.resume(args.subargs(2));
			}
			case RUNNING:
			{
				final LuaThread r = LuaThread.getRunning();
				return LuaThread.isMainThread(r) ? NIL : r;
			}
			case STATUS:
			{
				return valueOf(args.checkthread(1).getStatus());
			}
			case YIELD:
			{
				return LuaThread.yield(args);
			}
			case WRAP:
			{
				final LuaValue func = args.checkfunction(1);
				final LuaThread thread = new LuaThread(func, func.getfenv());
				LibCoroutine cl = new LibCoroutine();
				cl.setfenv(thread);
				cl._name = "wrapped";
				cl._opcode = WRAPPED;
				return cl;
			}
			case WRAPPED:
			{
				final LuaThread t = (LuaThread)env;
				final Varargs result = t.resume(args);
				if(result.arg1().toboolean())
				    return result.subargs(2);
				error(result.arg(2).tojstring());
			}
			//$FALL-THROUGH$
			default:
				return NONE;
		}
	}
}
