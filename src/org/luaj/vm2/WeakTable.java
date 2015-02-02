package org.luaj.vm2;

import java.lang.ref.WeakReference;
import org.luaj.vm2.lib.LibFunction2;

/**
 * Subclass of {@link LuaTable} that provides weak key and weak value semantics.
 * <p>
 * Normally these are not created directly, but indirectly when changing the mode
 * of a {@link LuaTable} as lua script executes.
 * <p>
 * However, calling the constructors directly when weak tables are required from
 * Java will reduce overhead.
 */
public class WeakTable extends LuaTable
{
	private boolean weakkeys, weakvalues;

	/**
	 * Construct a table with weak keys, weak values, or both
	 * @param weakkeys true to let the table have weak keys
	 * @param weakvalues true to let the table have weak values
	 */
	public WeakTable(boolean weakkeys, boolean weakvalues)
	{
		this(weakkeys, weakvalues, 0, 0);
	}

	/**
	 * Construct a table with weak keys, weak values, or both, and an initial capacity
	 * @param weakkeys true to let the table have weak keys
	 * @param weakvalues true to let the table have weak values
	 * @param narray capacity of array part
	 * @param nhash capacity of hash part
	 */
	protected WeakTable(boolean weakkeys, boolean weakvalues, int narray, int nhash)
	{
		super(narray, nhash);
		this.weakkeys = weakkeys;
		this.weakvalues = weakvalues;
	}

	/**
	 * Construct a table with weak keys, weak values, or both, and a source of initial data
	 * @param weakkeys true to let the table have weak keys
	 * @param weakvalues true to let the table have weak values
	 * @param source {@link LuaTable} containing the initial elements
	 */
	protected WeakTable(boolean weakkeys, boolean weakvalues, LuaTable source)
	{
		this(weakkeys, weakvalues, source.getArrayLength(), source.getHashLength());
		Varargs n;
		LuaValue k = NIL;
		while(!(k = ((n = source.next(k)).arg1())).isnil())
			rawset(k, n.arg(2));
		m_metatable = source.m_metatable;
	}

	@Override
	public void presize(int narray)
	{
		super.presize(narray);
	}

	/**
	 * Presize capacity of both array and hash parts.
	 * @param narray capacity of array part
	 * @param nhash capacity of hash part
	 */
	@Override
	public void presize(int narray, int nhash)
	{
		super.presize(narray, nhash);
	}

	@Override
	protected int getArrayLength()
	{
		return super.getArrayLength();
	}

	@Override
	protected int getHashLength()
	{
		return super.getHashLength();
	}

	@Override
	protected LuaTable changemode(boolean v_weakkeys, boolean v_weakvalues)
	{
		weakkeys = v_weakkeys;
		weakvalues = v_weakvalues;
		return this;
	}

	/**
	 * Self-sent message to convert a value to its weak counterpart
	 * @param value value to convert
	 * @return {@link LuaValue} that is a strong or weak reference, depending on type of {@code value}
	 */
	static LuaValue weaken(LuaValue value)
	{
		switch(value.type())
		{
			case LuaValue.TFUNCTION:
			case LuaValue.TTHREAD:
			case LuaValue.TTABLE:
				return new WeakValue(value);
			case LuaValue.TUSERDATA:
				return new WeakUserdata(value);
			default:
				return value;
		}
	}

	@Override
	public void rawset(int key, LuaValue value)
	{
		if(weakvalues)
		    value = weaken(value);
		super.rawset(key, value);
	}

	@Override
	public void rawset(LuaValue key, LuaValue value)
	{
		if(weakvalues)
		    value = weaken(value);
		if(weakkeys)
		{
			switch(key.type())
			{
				case LuaValue.TFUNCTION:
				case LuaValue.TTHREAD:
				case LuaValue.TTABLE:
				case LuaValue.TUSERDATA:
					key = value = new WeakEntry(key, value);
					break;
				default:
					break;
			}
		}
		super.rawset(key, value);
	}

	@Override
	public LuaValue rawget(int key)
	{
		return super.rawget(key).strongvalue();
	}

	@Override
	public LuaValue rawget(LuaValue key)
	{
		return super.rawget(key).strongvalue();
	}

	/** Get the hash value for a key
	 * key the key to look up
	 * */
	@Override
	protected LuaValue hashget(LuaValue key)
	{
		if(hashEntries > 0)
		{
			int i = hashFindSlot(key);
			if(hashEntries == 0)
			    return NIL;
			LuaValue v = hashValues[i];
			return v != null ? v : NIL;
		}
		return NIL;
	}

	// override to remove values for weak keys as we search
	@Override
	public int hashFindSlot(LuaValue key)
	{
		int i = (key.hashCode() & 0x7FFFFFFF) % hashKeys.length;
		LuaValue k;
		while((k = hashKeys[i]) != null)
		{
			if(k.isweaknil())
			{
				hashClearSlot(i);
				if(hashEntries == 0)
				    return 0;
			}
			else
			{
				if(k.raweq(key.strongkey()))
				    return i;
				i = (i + 1) % hashKeys.length;
			}
		}
		return i;
	}

	@Override
	public int maxn()
	{
		return super.maxn();
	}

	/**
	 * Get the next element after a particular key in the table
	 * @return key,value or nil
	 */
	@Override
	public Varargs next(LuaValue key)
	{
		while(true)
		{
			Varargs n = super.next(key);
			LuaValue k = n.arg1();
			if(k.isnil())
			    return NIL;
			LuaValue ks = k.strongkey();
			LuaValue vs = n.arg(2).strongvalue();
			if(ks.isnil() || vs.isnil())
			{
				super.rawset(k, NIL);
			}
			else
			{
				return varargsOf(ks, vs);
			}
		}
	}

	// ----------------- sort support -----------------------------
	@Override
	public void sort(final LuaValue comparator)
	{
		super.sort(new LibFunction2()
		{
			@Override
			public LuaValue call(LuaValue arg1, LuaValue arg2)
			{
				return comparator.call(arg1.strongvalue(), arg2.strongvalue());
			}
		});
	}

	/** Internal class to implement weak values.
	 * @see WeakTable
	 */
	static class WeakValue extends LuaValue
	{
		final WeakReference<LuaValue> ref;

		protected WeakValue(LuaValue value)
		{
			ref = new WeakReference<LuaValue>(value);
		}

		@Override
		public int type()
		{
			illegal("type", "weak value");
			return 0;
		}

		@Override
		public String typename()
		{
			illegal("typename", "weak value");
			return null;
		}

		@Override
		public String toString()
		{
			return "weak<" + ref.get() + ">";
		}

		@Override
		public LuaValue strongvalue()
		{
			LuaValue o = ref.get();
			return o != null ? o : NIL;
		}

		@Override
		public boolean raweq(LuaValue rhs)
		{
			LuaValue o = ref.get();
			return o != null && rhs.raweq(o);
		}

		@Override
		public boolean isweaknil()
		{
			return ref.get() == null;
		}
	}

	/** Internal class to implement weak userdata values.
	 * @see WeakTable
	 */
	static final class WeakUserdata extends WeakValue
	{
		private final WeakReference<Object> ob;
		private final LuaValue              mt;

		private WeakUserdata(LuaValue value)
		{
			super(value);
			ob = new WeakReference<Object>(value.touserdata());
			mt = value.getmetatable();
		}

		@Override
		public LuaValue strongvalue()
		{
			Object u = ref.get();
			if(u != null)
			    return (LuaValue)u;
			Object o = ob.get();
			return o != null ? userdataOf(o, mt) : NIL;
		}

		@Override
		public boolean raweq(LuaValue rhs)
		{
			if(!rhs.isuserdata())
			    return false;
			LuaValue v = ref.get();
			if(v != null && v.raweq(rhs))
			    return true;
			return rhs.touserdata() == ob.get();
		}

		@Override
		public boolean isweaknil()
		{
			return ob.get() == null || ref.get() == null;
		}
	}

	/** Internal class to implement weak table entries.
	 * @see WeakTable
	 */
	static final class WeakEntry extends LuaValue
	{
		final LuaValue weakkey;
		LuaValue       weakvalue;
		final int      keyhash;

		private WeakEntry(LuaValue key, LuaValue weakvalue)
		{
			this.weakkey = weaken(key);
			this.keyhash = key.hashCode();
			this.weakvalue = weakvalue;
		}

		@Override
		public LuaValue strongkey()
		{
			return weakkey.strongvalue();
		}

		// when looking up the value, look in the keys metatable
		@Override
		public LuaValue strongvalue()
		{
			LuaValue key = weakkey.strongvalue();
			if(key.isnil())
			    return weakvalue = NIL;
			return weakvalue.strongvalue();
		}

		@Override
		public int type()
		{
			return TNONE;
		}

		@Override
		public String typename()
		{
			illegal("typename", "weak entry");
			return null;
		}

		@Override
		public String toString()
		{
			return "weak<" + weakkey.strongvalue() + "," + strongvalue() + ">";
		}

		@Override
		public int hashCode()
		{
			return keyhash;
		}

		@Override
		public boolean raweq(LuaValue rhs)
		{
			//return rhs.raweq(weakkey.strongvalue());
			return weakkey.raweq(rhs);
		}

		@Override
		public boolean isweaknil()
		{
			return weakkey.isweaknil() || weakvalue.isweaknil();
		}
	}
}
