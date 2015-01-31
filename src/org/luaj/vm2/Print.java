package org.luaj.vm2;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Debug helper class to pretty-print lua bytecodes.
 * @see Prototype
 * @see LuaClosure
 */
public class Print extends Lua
{
	/** opcode names */
	private static final String   STRING_FOR_NULL = "null";

	private static final String[] OPNAMES         = {
	                                              "MOVE",
	                                              "LOADK",
	                                              "LOADBOOL",
	                                              "LOADNIL",
	                                              "GETUPVAL",
	                                              "GETGLOBAL",
	                                              "GETTABLE",
	                                              "SETGLOBAL",
	                                              "SETUPVAL",
	                                              "SETTABLE",
	                                              "NEWTABLE",
	                                              "SELF",
	                                              "ADD",
	                                              "SUB",
	                                              "MUL",
	                                              "DIV",
	                                              "MOD",
	                                              "POW",
	                                              "UNM",
	                                              "NOT",
	                                              "LEN",
	                                              "CONCAT",
	                                              "JMP",
	                                              "EQ",
	                                              "LT",
	                                              "LE",
	                                              "TEST",
	                                              "TESTSET",
	                                              "CALL",
	                                              "TAILCALL",
	                                              "RETURN",
	                                              "FORLOOP",
	                                              "FORPREP",
	                                              "TFORLOOP",
	                                              "SETLIST",
	                                              "CLOSE",
	                                              "CLOSURE",
	                                              "VARARG",
	                                              };

	static void printString(PrintStream ps, LuaString s)
	{
		ps.print('"');
		for(int i = 0, n = s.m_length; i < n; i++)
		{
			int c = s.m_bytes[s.m_offset + i];
			if(c >= ' ' && c <= '~' && c != '\"' && c != '\\')
				ps.print((char)c);
			else
			{
				switch(c)
				{
					case '"':
						ps.print("\\\"");
						break;
					case '\\':
						ps.print("\\\\");
						break;
					case 0x0007: /* bell */
						ps.print("\\a");
						break;
					case '\b': /* backspace */
						ps.print("\\b");
						break;
					case '\f': /* form feed */
						ps.print("\\f");
						break;
					case '\t': /* tab */
						ps.print("\\t");
						break;
					case '\r': /* carriage return */
						ps.print("\\r");
						break;
					case '\n': /* newline */
						ps.print("\\n");
						break;
					case 0x000B: /* vertical tab */
						ps.print("\\v");
						break;
					default:
						ps.print('\\');
						ps.print(Integer.toString(1000 + 0xff & c).substring(1));
						break;
				}
			}
		}
		ps.print('"');
	}

	static void printValue(PrintStream ps, LuaValue v)
	{
		switch(v.type())
		{
			case LuaValue.TSTRING:
				printString(ps, (LuaString)v);
				break;
			default:
				ps.print(v.tojstring());

		}
	}

	static void printConstant(PrintStream ps, Prototype f, int i)
	{
		printValue(ps, f.k[i]);
	}

	/**
	 * Print the code in a prototype
	 * @param f the {@link Prototype}
	 */
	public static void printCode(PrintStream ps, Prototype f)
	{
		int[] code = f.code;
		int pc, n = code.length;
		for(pc = 0; pc < n; pc++)
		{
			printOpCode(ps, f, pc);
			ps.println();
		}
	}

	/**
	 * Print an opcode in a prototype
	 * @param ps the {@link PrintStream} to print to
	 * @param f the {@link Prototype}
	 * @param pc the program counter to look up and print
	 */
	public static void printOpCode(PrintStream ps, Prototype f, int pc)
	{
		int[] code = f.code;
		int i = code[pc];
		int o = GET_OPCODE(i);
		int a = GETARG_A(i);
		int b = GETARG_B(i);
		int c = GETARG_C(i);
		int bx = GETARG_Bx(i);
		int sbx = GETARG_sBx(i);
		int line = getline(f, pc);
		ps.print("  " + (pc + 1) + "  ");
		if(line > 0)
			ps.print("[" + line + "]  ");
		else
			ps.print("[-]  ");
		ps.print(OPNAMES[o] + "  ");
		switch(getOpMode(o))
		{
			case iABC:
				ps.print(a);
				if(getBMode(o) != OpArgN)
				    ps.print(" " + (ISK(b) ? (-1 - INDEXK(b)) : b));
				if(getCMode(o) != OpArgN)
				    ps.print(" " + (ISK(c) ? (-1 - INDEXK(c)) : c));
				break;
			case iABx:
				if(getBMode(o) == OpArgK)
				{
					ps.print(a + " " + (-1 - bx));
				}
				else
				{
					ps.print(a + " " + (bx));
				}
				break;
			case iAsBx:
				if(o == OP_JMP)
					ps.print(sbx);
				else
					ps.print(a + " " + sbx);
				break;
		}
		switch(o)
		{
			case OP_LOADK:
				ps.print("  ; ");
				printConstant(ps, f, bx);
				break;
			case OP_GETUPVAL:
			case OP_SETUPVAL:
				ps.print("  ; ");
				if(f.upvalues.length > b)
					printValue(ps, f.upvalues[b]);
				else
					ps.print("-");
				break;
			case OP_GETGLOBAL:
			case OP_SETGLOBAL:
				ps.print("  ; ");
				printConstant(ps, f, bx);
				break;
			case OP_GETTABLE:
			case OP_SELF:
				if(ISK(c))
				{
					ps.print("  ; ");
					printConstant(ps, f, INDEXK(c));
				}
				break;
			case OP_SETTABLE:
			case OP_ADD:
			case OP_SUB:
			case OP_MUL:
			case OP_DIV:
			case OP_POW:
			case OP_EQ:
			case OP_LT:
			case OP_LE:
				if(ISK(b) || ISK(c))
				{
					ps.print("  ; ");
					if(ISK(b))
						printConstant(ps, f, INDEXK(b));
					else
						ps.print("-");
					ps.print(" ");
					if(ISK(c))
						printConstant(ps, f, INDEXK(c));
					else
						ps.print("-");
				}
				break;
			case OP_JMP:
			case OP_FORLOOP:
			case OP_FORPREP:
				ps.print("  ; to " + (sbx + pc + 2));
				break;
			case OP_CLOSURE:
				ps.print("  ; " + f.p[bx].getClass().getName());
				break;
			case OP_SETLIST:
				if(c == 0)
					ps.print("  ; " + code[++pc]);
				else
					ps.print("  ; " + c);
				break;
			case OP_VARARG:
				ps.print("  ; is_vararg=" + f.is_vararg);
				break;
			default:
				break;
		}
	}

	private static int getline(Prototype f, int pc)
	{
		return pc > 0 && f.lineinfo != null && pc < f.lineinfo.length ? f.lineinfo[pc] : -1;
	}

	static void printHeader(PrintStream ps, Prototype f)
	{
		String s = String.valueOf(f.source);
		if(s.startsWith("@") || s.startsWith("="))
			s = s.substring(1);
		else if("\033Lua".equals(s))
			s = "(bstring)";
		else
			s = "(string)";
		String a = (f.linedefined == 0) ? "main" : "function";
		ps.print("\n%" + a + " <" + s + ":" + f.linedefined + ","
		        + f.lastlinedefined + "> (" + f.code.length + " instructions, "
		        + f.code.length * 4 + " bytes at " + id() + ")\n");
		ps.print(f.numparams + " param, " + f.maxstacksize + " slot, "
		        + f.upvalues.length + " upvalue, ");
		ps.print(f.locvars.length + " local, " + f.k.length
		        + " constant, " + f.p.length + " function\n");
	}

	static void printConstants(PrintStream ps, Prototype f)
	{
		int i, n = f.k.length;
		ps.print("constants (" + n + ") for " + id() + ":\n");
		for(i = 0; i < n; i++)
		{
			ps.print("  " + (i + 1) + "  ");
			printValue(ps, f.k[i]);
			ps.print("\n");
		}
	}

	static void printLocals(PrintStream ps, Prototype f)
	{
		int i, n = f.locvars.length;
		ps.print("locals (" + n + ") for " + id() + ":\n");
		for(i = 0; i < n; i++)
		{
			ps.println("  " + i + "  " + f.locvars[i].varname + " " + (f.locvars[i].startpc + 1) + " " + (f.locvars[i].endpc + 1));
		}
	}

	static void printUpValues(PrintStream ps, Prototype f)
	{
		int i, n = f.upvalues.length;
		ps.print("upvalues (" + n + ") for " + id() + ":\n");
		for(i = 0; i < n; i++)
		{
			ps.print("  " + i + "  " + f.upvalues[i] + "\n");
		}
	}

	public static void print(PrintStream ps, Prototype p)
	{
		printFunction(ps, p, true);
	}

	public static void printFunction(PrintStream ps, Prototype f, boolean full)
	{
		int i, n = f.p.length;
		printHeader(ps, f);
		printCode(ps, f);
		if(full)
		{
			printConstants(ps, f);
			printLocals(ps, f);
			printUpValues(ps, f);
		}
		for(i = 0; i < n; i++)
			printFunction(ps, f.p[i], full);
	}

	private static void format(PrintStream ps, String s, int maxcols)
	{
		int n = s.length();
		if(n > maxcols)
			ps.print(s.substring(0, maxcols));
		else
		{
			ps.print(s);
			for(int i = maxcols - n; --i >= 0;)
				ps.print(' ');
		}
	}

	private static String id()
	{
		return "Proto";
	}

	/**
	 * Print the state of a {@link LuaClosure} that is being executed
	 * @param cl the {@link LuaClosure}
	 * @param pc the program counter
	 * @param stack the stack of {@link LuaValue}
	 * @param top the top of the stack
	 * @param varargs any {@link Varargs} value that may apply
	 */
	public static void printState(LuaClosure cl, int pc, LuaValue[] stack, int top, Varargs varargs)
	{
		// print opcode into buffer
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		printOpCode(ps, cl.p, pc);
		ps.flush();
		ps.close();
		ps = System.out;
		format(ps, baos.toString(), 50);

		// print stack
		ps.print('[');
		for(int i = 0; i < stack.length; i++)
		{
			LuaValue v = stack[i];
			if(v == null)
				ps.print(STRING_FOR_NULL);
			else
				switch(v.type())
				{
					case LuaValue.TSTRING:
						LuaString s = v.checkstring();
						ps.print(s.length() < 48 ?
						        s.tojstring() :
						        s.substring(0, 32).tojstring() + "...+" + (s.length() - 32) + "b");
						break;
					case LuaValue.TFUNCTION:
						ps.print((v instanceof LuaClosure) ?
						        ((LuaClosure)v).p.toString() : v.tojstring());
						break;
					case LuaValue.TUSERDATA:
						Object o = v.touserdata();
						if(o != null)
						{
							String n = o.getClass().getName();
							n = n.substring(n.lastIndexOf('.') + 1);
							ps.print(n + ": " + Integer.toHexString(o.hashCode()));
						}
						else
						{
							ps.print(v.toString());
						}
						break;
					default:
						ps.print(v.tojstring());
				}
			if(i + 1 == top)
			    ps.print(']');
			ps.print(" | ");
		}
		ps.print(varargs);
		ps.println();
	}
}
