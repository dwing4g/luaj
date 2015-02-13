package org.luaj.vm2.lib;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;

/** The {@link JsePlatform} class is a convenience class to standardize
 * how globals tables are initialized for the JSE platform.
 * <p>
 * It is used to allocate either a set of standard globals using
 * {@link #standardGlobals()} or debug globals using {@link #debugGlobals()}
 * <p>
 * A simple example of initializing globals and using them from Java is:
 * <pre> {@code
 * LuaValue _G = JsePlatform.standardGlobals();
 * _G.get("print").call(LuaValue.valueOf("hello, world"));
 * } </pre>
 * <p>
 * Once globals are created, a simple way to load and run a script is:
 * <pre> {@code
 * LoadState.load( new FileInputStream("main.lua"), "main.lua", _G ).call();
 * } </pre>
 * <p>
 * although {@code require} could also be used:
 * <pre> {@code
 * _G.get("require").call(LuaValue.valueOf("main"));
 * } </pre>
 * For this to succeed, the file "main.lua" must be in the current directory or a resource.
 * See {@link LibBase} for details on finding scripts.
 * <p>
 * The standard globals will contain all standard libraries plus {@code luajava}:
 * <ul>
 * <li>{@link LibBase}</li>
 * <li>{@link LibPackage}</li>
 * <li>{@link LibTable}</li>
 * <li>{@link LibString}</li>
 * <li>{@link LibCoroutine}</li>
 * <li>{@link JseMathLib}</li>
 * <li>{@link JseIoLib}</li>
 * <li>{@link JseOsLib}</li>
 * <li>{@link LibLuajava}</li>
 * </ul>
 * In addition, the {@link LuaC} compiler is installed so lua files may be loaded in their source form.
 * <p>
 * The debug globals are simply the standard globals plus the {@code debug} library {@link LibDebug}.
 * <p>
 * The class ensures that initialization is done in the correct order,
 * and that linkage is made to {@link LuaThread#setGlobals(LuaValue)}.
 */
public final class JsePlatform
{
	/**
	 * Create a standard set of globals for JSE including all the libraries.
	 *
	 * @return Table of globals initialized with the standard JSE libraries
	 * @see #debugGlobals()
	 * @see JsePlatform
	 */
	public static LuaTable standardGlobals()
	{
		LuaTable _G = new LuaTable();
		_G.load(new LibBase());
		_G.load(new LibPackage());
		_G.load(new LibTable());
		_G.load(new LibString());
		_G.load(new LibCoroutine());
		_G.load(new LibMath());
		_G.load(new LibIo());
		_G.load(new LibOs());
		_G.load(new LibLuajava());
		LuaThread.setGlobals(_G);
		LuaC.install();
		return _G;
	}

	/** Create standard globals including the {@link debug} library.
	 *
	 * @return Table of globals initialized with the standard JSE and debug libraries
	 * @see #standardGlobals()
	 * @see JsePlatform
	 * @see LibDebug
	 */
	public static LuaTable debugGlobals()
	{
		LuaTable _G = standardGlobals();
		_G.load(new LibDebug());
		return _G;
	}
}
