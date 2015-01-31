package org.luaj.vm2.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LoadState.LuaCompiler;
import org.luaj.vm2.LocVars;
import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Compiler for Lua.
 * <p>
 * Compiles lua source files into lua bytecode within a {@link Prototype},
 * loads lua binary files directly into a{@link Prototype},
 * and optionaly instantiates a {@link LuaClosure} around the result
 * using a user-supplied environment.
 * <p>
 * Implements the {@link LuaCompiler} interface for loading
 * initialized chunks, which is an interface common to
 * lua bytecode compiling and java bytecode compiling.
 * <p>
 * Teh {@link LuaC} compiler is installed by default by both the
 * {@link JsePlatform} and {@link JmePlatform} classes,
 * so in the following example, the default {@link LuaC} compiler
 * will be used:
 * <pre> {@code
 * LuaValue _G = JsePlatform.standardGlobals();
 * LoadState.load( new ByteArrayInputStream("print 'hello'".getBytes()), "main.lua", _G ).call();
 * } </pre>
 * @see LuaCompiler
 * @see JsePlatform
 * @see BaseLib
 * @see LuaValue
 * @see Prototype
 */
public class LuaC extends Lua implements LuaCompiler
{
	public static final LuaC instance = new LuaC();

	/** Install the compiler so that LoadState will first
	 * try to use it when handed bytes that are
	 * not already a compiled lua chunk.
	 */
	public static void install()
	{
		LoadState.compiler = instance;
	}

	protected static void _assert(boolean b)
	{
		if(!b)
		    throw new LuaError("compiler assert failed");
	}

	public static final int MAXSTACK         = 250;
	static final int        LUAI_MAXUPVALUES = 60;
	static final int        LUAI_MAXVARS     = 200;

	static void SET_OPCODE(InstructionPtr i, int o)
	{
		i.set((i.get() & (MASK_NOT_OP)) | ((o << POS_OP) & MASK_OP));
	}

	static void SETARG_A(InstructionPtr i, int u)
	{
		i.set((i.get() & (MASK_NOT_A)) | ((u << POS_A) & MASK_A));
	}

	static void SETARG_B(InstructionPtr i, int u)
	{
		i.set((i.get() & (MASK_NOT_B)) | ((u << POS_B) & MASK_B));
	}

	static void SETARG_C(InstructionPtr i, int u)
	{
		i.set((i.get() & (MASK_NOT_C)) | ((u << POS_C) & MASK_C));
	}

	static void SETARG_Bx(InstructionPtr i, int u)
	{
		i.set((i.get() & (MASK_NOT_Bx)) | ((u << POS_Bx) & MASK_Bx));
	}

	static void SETARG_sBx(InstructionPtr i, int u)
	{
		SETARG_Bx(i, u + MAXARG_sBx);
	}

	static int CREATE_ABC(int o, int a, int b, int c)
	{
		return ((o << POS_OP) & MASK_OP) |
		        ((a << POS_A) & MASK_A) |
		        ((b << POS_B) & MASK_B) |
		        ((c << POS_C) & MASK_C);
	}

	static int CREATE_ABx(int o, int a, int bc)
	{
		return ((o << POS_OP) & MASK_OP) |
		        ((a << POS_A) & MASK_A) |
		        ((bc << POS_Bx) & MASK_Bx);
	}

	// vector reallocation

	static LuaValue[] realloc(LuaValue[] v, int n)
	{
		LuaValue[] a = new LuaValue[n];
		if(v != null)
		    System.arraycopy(v, 0, a, 0, Math.min(v.length, n));
		return a;
	}

	static Prototype[] realloc(Prototype[] v, int n)
	{
		Prototype[] a = new Prototype[n];
		if(v != null)
		    System.arraycopy(v, 0, a, 0, Math.min(v.length, n));
		return a;
	}

	static LuaString[] realloc(LuaString[] v, int n)
	{
		LuaString[] a = new LuaString[n];
		if(v != null)
		    System.arraycopy(v, 0, a, 0, Math.min(v.length, n));
		return a;
	}

	static LocVars[] realloc(LocVars[] v, int n)
	{
		LocVars[] a = new LocVars[n];
		if(v != null)
		    System.arraycopy(v, 0, a, 0, Math.min(v.length, n));
		return a;
	}

	static int[] realloc(int[] v, int n)
	{
		int[] a = new int[n];
		if(v != null)
		    System.arraycopy(v, 0, a, 0, Math.min(v.length, n));
		return a;
	}

	static byte[] realloc(byte[] v, int n)
	{
		byte[] a = new byte[n];
		if(v != null)
		    System.arraycopy(v, 0, a, 0, Math.min(v.length, n));
		return a;
	}

	public int                    nCcalls;
	HashMap<LuaString, LuaString> strings;

	protected LuaC()
	{
	}

	private LuaC(HashMap<LuaString, LuaString> strings)
	{
		this.strings = strings;
	}

	/** Load into a Closure or LuaFunction, with the supplied initial environment */
	@Override
	public LuaFunction load(InputStream stream, String name, LuaValue env) throws IOException
	{
		return new LuaClosure(compile(stream, name), env);
	}

	/** Compile a prototype or load as a binary chunk */
	public static Prototype compile(InputStream stream, String name) throws IOException
	{
		int firstByte = stream.read();
		return firstByte == '\033' ?
		        LoadState.loadBinaryChunk(firstByte, stream, name) :
		        new LuaC(new HashMap<LuaString, LuaString>()).luaY_parser(firstByte, stream, name);
	}

	/** Parse the input */
	private Prototype luaY_parser(int firstByte, InputStream z, String name)
	{
		LexState lexstate = new LexState(this, z);
		FuncState funcstate = new FuncState();
		// lexstate.buff = buff;
		lexstate.setinput(this, firstByte, z, LuaValue.valueOf(name));
		lexstate.open_func(funcstate);
		/* main func. is always vararg */
		funcstate._f.is_vararg = Lua.VARARG_ISVARARG;
		funcstate._f.source = LuaValue.valueOf(name);
		lexstate.next(); /* read first token */
		lexstate.chunk();
		lexstate.check(LexState.TK_EOS);
		lexstate.close_func();
		LuaC._assert(funcstate.prev == null);
		LuaC._assert(funcstate._f.nups == 0);
		LuaC._assert(lexstate._fs == null);
		return funcstate._f;
	}

	// look up and keep at most one copy of each string
	public LuaString newTString(byte[] bytes, int offset, int len)
	{
		LuaString tmp = LuaString.valueOf(bytes, offset, len);
		LuaString v = strings.get(tmp);
		if(v == null)
		{
			// must copy bytes, since bytes could be from reusable buffer
			byte[] copy = new byte[len];
			System.arraycopy(bytes, offset, copy, 0, len);
			v = LuaString.valueOf(copy);
			strings.put(v, v);
		}
		return v;
	}

	public static String pushfstring(String string)
	{
		return string;
	}

	public static LuaFunction load(Prototype p, LuaValue env)
	{
		return new LuaClosure(p, env);
	}
}
