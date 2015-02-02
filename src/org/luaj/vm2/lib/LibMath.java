package org.luaj.vm2.lib;

import java.util.Random;
import org.luaj.vm2.LuaDouble;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Subclass of {@link LibFunction} which implements the lua standard {@code math}
 * library.
 * <p>
 * It contains only the math library support that is possible on JME.
 * For a more complete implementation based on math functions specific to JSE
 * use {@link JseMathLib}.
 * In Particular the following math functions are <b>not</b> implemented by this library:
 * <ul>
 * <li>acos</li>
 * <li>asin</li>
 * <li>atan</li>
 * <li>cosh</li>
 * <li>log</li>
 * <li>log10</li>
 * <li>sinh</li>
 * <li>tanh</li>
 * <li>atan2</li>
 * </ul>
 * <p>
 * The implementations of {@code exp()} and {@code pow()} are constructed by
 * hand for JME, so will be slower and less accurate than when executed on the JSE platform.
 * <p>
 * To instantiate and use it directly,
 * link it into your globals table via {@link LuaValue#load(LuaValue)} using code such as:
 * <pre> {@code
 * LuaTable _G = new LuaTable();
 * LuaThread.setGlobals(_G);
 * _G.load(new BaseLib());
 * _G.load(new PackageLib());
 * _G.load(new MathLib());
 * System.out.println( _G.get("math").get("sqrt").call( LuaValue.valueOf(2) ) );
 * } </pre>
 * Doing so will ensure the library is properly initialized
 * and loaded into the globals table.
 * <p>
 * This has been implemented to match as closely as possible the behavior in the corresponding library in C.
 * @see LibFunction
 * @see <a href="http://www.lua.org/manual/5.1/manual.html#5.6">http://www.lua.org/manual/5.1/manual.html#5.6</a>
 */
public class LibMath extends LibFunction1
{
	private Random random;

	public LibMath()
	{
	}

	@Override
	public LuaValue call(LuaValue arg)
	{
		LuaTable t = new LuaTable(0, 30);
		t.set("pi", Math.PI);
		t.set("huge", LuaDouble.POSINF);
		bind(t, MathLib1.class, new String[] {
		        "abs", "ceil", "cos", "deg",
		        "exp", "floor", "rad", "sin",
		        "sqrt", "tan" });
		bind(t, MathLib2.class, new String[] {
		        "fmod", "ldexp", "pow", });
		bind(t, MathLibV.class, new String[] {
		        "frexp", "max", "min", "modf",
		        "randomseed", "random", });
		bind(t, JseMathLib1.class, new String[] {
		        "acos", "asin", "atan", "cosh",
		        "exp", "log", "log10", "sinh",
		        "tanh" });
		bind(t, JseMathLib2.class, new String[] {
		        "atan2", "pow", });
		((MathLibV)t.get("randomseed")).mathlib = this;
		((MathLibV)t.get("random")).mathlib = this;
		env.set("math", t);
		LibPackage.instance.LOADED.set("math", t);
		return t;
	}

	public static final class JseMathLib1 extends LibFunction1
	{
		@Override
		public LuaValue call(LuaValue arg)
		{
			switch(_opcode)
			{
				case 0:
					return valueOf(Math.acos(arg.checkdouble()));
				case 1:
					return valueOf(Math.asin(arg.checkdouble()));
				case 2:
					return valueOf(Math.atan(arg.checkdouble()));
				case 3:
					return valueOf(Math.cosh(arg.checkdouble()));
				case 4:
					return valueOf(Math.exp(arg.checkdouble()));
				case 5:
					return valueOf(Math.log(arg.checkdouble()));
				case 6:
					return valueOf(Math.log10(arg.checkdouble()));
				case 7:
					return valueOf(Math.sinh(arg.checkdouble()));
				case 8:
					return valueOf(Math.tanh(arg.checkdouble()));
			}
			return NIL;
		}
	}

	public static final class JseMathLib2 extends LibFunction2
	{
		@Override
		public LuaValue call(LuaValue arg1, LuaValue arg2)
		{
			switch(_opcode)
			{
				case 0:
					return valueOf(Math.atan2(arg1.checkdouble(), arg2.checkdouble()));
				case 1:
					return valueOf(Math.pow(arg1.checkdouble(), arg2.checkdouble()));
			}
			return NIL;
		}
	}

	static final class MathLib1 extends LibFunction1
	{
		@Override
		public LuaValue call(LuaValue arg)
		{
			switch(_opcode)
			{
				case 0:
					return valueOf(Math.abs(arg.checkdouble()));
				case 1:
					return valueOf(Math.ceil(arg.checkdouble()));
				case 2:
					return valueOf(Math.cos(arg.checkdouble()));
				case 3:
					return valueOf(Math.toDegrees(arg.checkdouble()));
				case 4:
					return dpow(Math.E, arg.checkdouble());
				case 5:
					return valueOf(Math.floor(arg.checkdouble()));
				case 6:
					return valueOf(Math.toRadians(arg.checkdouble()));
				case 7:
					return valueOf(Math.sin(arg.checkdouble()));
				case 8:
					return valueOf(Math.sqrt(arg.checkdouble()));
				case 9:
					return valueOf(Math.tan(arg.checkdouble()));
			}
			return NIL;
		}
	}

	static final class MathLib2 extends LibFunction2
	{
		protected LibMath mathlib;

		@Override
		public LuaValue call(LuaValue arg1, LuaValue arg2)
		{
			switch(_opcode)
			{
				case 0:
				{ // fmod
					double x = arg1.checkdouble();
					double y = arg2.checkdouble();
					double q = x / y;
					double f = x - y * (q >= 0 ? Math.floor(q) : Math.ceil(q));
					return valueOf(f);
				}
				case 1:
				{ // ldexp
					double x = arg1.checkdouble();
					double y = arg2.checkdouble() + 1023.5;
					long e = (long)((0 != (1 & ((int)y))) ? Math.floor(y) : Math.ceil(y - 1));
					return valueOf(x * Double.longBitsToDouble(e << 52));
				}
				case 2:
				{ // pow
					return dpow(arg1.checkdouble(), arg2.checkdouble());
				}
			}
			return NIL;
		}
	}

	/** compute power using installed math library, or default if there is no math library installed */
	public static LuaValue dpow(double a, double b)
	{
		return LuaDouble.valueOf(LibMath.dpow_lib(a, b));
	}

	public static double dpow_d(double a, double b)
	{
		return LibMath.dpow_lib(a, b);
	}

	/**
	 * Hook to override default dpow behavior with faster implementation.
	 */
	public static double dpow_lib(double a, double b)
	{
		return Math.pow(a, b);
	}

	static final class MathLibV extends LibFunctionV
	{
		protected LibMath mathlib;

		@Override
		public Varargs invoke(Varargs args)
		{
			switch(_opcode)
			{
				case 0:
				{ // frexp
					double x = args.checkdouble(1);
					if(x == 0) return varargsOf(ZERO, ZERO);
					long bits = Double.doubleToLongBits(x);
					double m = ((bits & (~(-1L << 52))) + (1L << 52)) * ((bits >= 0) ? (.5 / (1L << 52)) : (-.5 / (1L << 52)));
					double e = (((int)(bits >> 52)) & 0x7ff) - 1022;
					return varargsOf(valueOf(m), valueOf(e));
				}
				case 1:
				{ // max
					double m = args.checkdouble(1);
					for(int i = 2, n = args.narg(); i <= n; ++i)
						m = Math.max(m, args.checkdouble(i));
					return valueOf(m);
				}
				case 2:
				{ // min
					double m = args.checkdouble(1);
					for(int i = 2, n = args.narg(); i <= n; ++i)
						m = Math.min(m, args.checkdouble(i));
					return valueOf(m);
				}
				case 3:
				{ // modf
					double x = args.checkdouble(1);
					double intPart = (x > 0) ? Math.floor(x) : Math.ceil(x);
					double fracPart = x - intPart;
					return varargsOf(valueOf(intPart), valueOf(fracPart));
				}
				case 4:
				{ // randomseed
					long seed = args.checklong(1);
					mathlib.random = new Random(seed);
					return NONE;
				}
				case 5:
				{ // random
					if(mathlib.random == null)
					    mathlib.random = new Random();

					switch(args.narg())
					{
						case 0:
							return valueOf(mathlib.random.nextDouble());
						case 1:
						{
							int m = args.checkint(1);
							if(m < 1) argerror(1, "interval is empty");
							return valueOf(1 + mathlib.random.nextInt(m));
						}
						default:
						{
							int m = args.checkint(1);
							int n = args.checkint(2);
							if(n < m) argerror(2, "interval is empty");
							return valueOf(m + mathlib.random.nextInt(n + 1 - m));
						}
					}
				}
			}
			return NONE;
		}
	}
}
