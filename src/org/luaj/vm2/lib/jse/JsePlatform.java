package org.luaj.vm2.lib.jse;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.OsLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;

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
 * See {@link BaseLib} for details on finding scripts.
 * <p>
 * The standard globals will contain all standard libraries plus {@code luajava}:
 * <ul>
 * <li>{@link BaseLib}</li>
 * <li>{@link PackageLib}</li>
 * <li>{@link TableLib}</li>
 * <li>{@link StringLib}</li>
 * <li>{@link CoroutineLib}</li>
 * <li>{@link JseMathLib}</li>
 * <li>{@link JseIoLib}</li>
 * <li>{@link JseOsLib}</li>
 * <li>{@link LuajavaLib}</li>
 * </ul>
 * In addition, the {@link LuaC} compiler is installed so lua files may be loaded in their source form.
 * <p>
 * The debug globals are simply the standard globals plus the {@code debug} library {@link DebugLib}.
 * <p>
 * The class ensures that initialization is done in the correct order,
 * and that linkage is made to {@link LuaThread#setGlobals(LuaValue)}.
 */
public class JsePlatform
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
		_G.load(new BaseLib());
		_G.load(new PackageLib());
		_G.load(new TableLib());
		_G.load(new StringLib());
		_G.load(new CoroutineLib());
		_G.load(new MathLib());
		_G.load(new JseIoLib());
		_G.load(new OsLib());
		_G.load(new LuajavaLib());
		LuaThread.setGlobals(_G);
		LuaC.install();
		return _G;
	}

	/** Create standard globals including the {@link debug} library.
	 *
	 * @return Table of globals initialized with the standard JSE and debug libraries
	 * @see #standardGlobals()
	 * @see JsePlatform
	 * @see DebugLib
	 */
	public static LuaTable debugGlobals()
	{
		LuaTable _G = standardGlobals();
		_G.load(new DebugLib());
		return _G;
	}
}
