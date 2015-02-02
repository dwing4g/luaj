package org.luaj.vm2.lib;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Subclass of {@link LibFunction} which implements the lua standard {@code table}
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
 * _G.load(new TableLib());
 * LuaValue tbl = LuaValue.listOf( new LuaValue[] {
 * 		LuaValue.valueOf( "abc" ),
 * 		LuaValue.valueOf( "def" ) } );
 * LuaValue sep = LuaValue.valueOf( "-" );
 * System.out.println( _G.get("table").get("concat").call( tbl, sep ) );
 * } </pre>
 * Doing so will ensure the library is properly initialized
 * and loaded into the globals table.
 * <p>
 * This has been implemented to match as closely as possible the behavior in the corresponding library in C.
 * @see LibFunction
 * @see JsePlatform
 * @see <a href="http://www.lua.org/manual/5.1/manual.html#5.5">http://www.lua.org/manual/5.1/manual.html#5.5</a>
 */
public class LibTable extends LibFunction1
{
	public LibTable()
	{
	}

	private LuaTable init()
	{
		LuaTable t = new LuaTable();
		bind(t, LibTable.class, new String[] { "getn", "maxn", }, 1);
		bind(t, TableLibV.class, new String[] {
		        "remove", "concat", "insert", "sort", "foreach", "foreachi", });
		env.set("table", t);
		LibPackage.instance.LOADED.set("table", t);
		return t;
	}

	@Override
	public LuaValue call(LuaValue arg)
	{
		switch(_opcode)
		{
			case 0: // init library
				return init();
			case 1: // "getn" (table) -> number
				return arg.checktable().getn();
			case 2: // "maxn"  (table) -> number
				return valueOf(arg.checktable().maxn());
		}
		return NIL;
	}

	static final class TableLibV extends LibFunctionV
	{
		@Override
		public Varargs invoke(Varargs args)
		{
			switch(_opcode)
			{
				case 0:
				{ // "remove" (table [, pos]) -> removed-ele
					LuaTable table = args.checktable(1);
					int pos = args.narg() > 1 ? args.checkint(2) : 0;
					return table.remove(pos);
				}
				case 1:
				{ // "concat" (table [, sep [, i [, j]]]) -> string
					LuaTable table = args.checktable(1);
					return table.concat(
					        args.optstring(2, LuaValue.EMPTYSTRING),
					        args.optint(3, 1),
					        args.isvalue(4) ? args.checkint(4) : table.length());
				}
				case 2:
				{ // "insert" (table, [pos,] value) -> prev-ele
					final LuaTable table = args.checktable(1);
					final int pos = args.narg() > 2 ? args.checkint(2) : 0;
					final LuaValue value = args.arg(args.narg() > 2 ? 3 : 2);
					table.insert(pos, value);
					return NONE;
				}
				case 3:
				{ // "sort" (table [, comp]) -> void
					LuaTable table = args.checktable(1);
					LuaValue compare = (args.isnoneornil(2) ? NIL : args.checkfunction(2));
					table.sort(compare);
					return NONE;
				}
				case 4:
				{ // (table, func) -> void
					return args.checktable(1).foreach(args.checkfunction(2));
				}
				case 5:
				{ // "foreachi" (table, func) -> void
					return args.checktable(1).foreachi(args.checkfunction(2));
				}
			}
			return NONE;
		}
	}
}
