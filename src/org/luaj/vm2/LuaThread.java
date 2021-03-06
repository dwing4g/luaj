package org.luaj.vm2;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;
import org.luaj.vm2.lib.LibDebug;

/**
 * Subclass of {@link LuaValue} that implements
 * a lua coroutine thread using Java Threads.
 * <p>
 * A LuaThread is typically created in response to a scripted call to
 * {@code coroutine.create()}
 * <p>
 * The threads must be initialized with the globals, so that
 * the global environment may be passed along according to rules of lua.
 * This is done via a call to {@link #setGlobals(LuaValue)}
 * at some point during globals initialization.
 * See LibBase for additional documentation and example code.
 * <p>
 * The utility classes JsePlatform see to it that this initialization is done properly.
 * For this reason it is highly recommended to use one of these classes
 * when initializing globals.
 * <p>
 * The behavior of coroutine threads matches closely the behavior
 * of C coroutine library.  However, because of the use of Java threads
 * to manage call state, it is possible to yield from anywhere in luaj.
 * <p>
 * Each Java thread wakes up at regular intervals and checks a weak reference
 * to determine if it can ever be resumed.  If not, it throws
 * {@link OrphanedThread} which is an {@link Error}.
 * Applications should not catch {@link OrphanedThread}, because it can break
 * the thread safety of luaj.
 */
public final class LuaThread extends LuaValue
{
	public static LuaValue          s_metatable;

	private static final AtomicLong coroutine_count              = new AtomicLong();

	/** Interval at which to check for lua threads that are no longer referenced.
	 * This can be changed by Java startup code if desired.
	 */
	private static final long       thread_orphan_check_interval = 30000;

	private static final int        STATUS_INITIAL               = 0;
	private static final int        STATUS_SUSPENDED             = 1;
	private static final int        STATUS_RUNNING               = 2;
	private static final int        STATUS_NORMAL                = 3;
	private static final int        STATUS_DEAD                  = 4;
	private static final String[]   STATUS_NAMES                 = {
	                                                             "suspended",
	                                                             "suspended",
	                                                             "running",
	                                                             "normal",
	                                                             "dead", };

	public static final int         MAX_CALLSTACK                = 256;

	private static final LuaThread  main_thread                  = new LuaThread();

	// state of running thread including call stack
	private static LuaThread        running_thread               = main_thread;

	private LuaValue                _env;
	private final State             _state;

	/** Field to hold state of error condition during debug hook function calls. */
	LuaValue                        _err;

	private final CallStack         _callstack                   = new CallStack();

	/** Thread-local used by DebugLib to store debugging state.  */
	public Object                   _debugState;

	/** Private constructor for main thread only */
	private LuaThread()
	{
		_state = new State(this, null);
		_state._status = STATUS_RUNNING;
	}

	/**
	 * Create a LuaThread around a function and environment
	 * @param func The function to execute
	 * @param env The environment to apply to the thread
	 */
	public LuaThread(LuaValue func, LuaValue env)
	{
		LuaValue.assert_(func != null, "function cannot be null");
		_env = env;
		_state = new State(this, func);
	}

	@Override
	public int type()
	{
		return LuaValue.TTHREAD;
	}

	@Override
	public String typename()
	{
		return "thread";
	}

	@Override
	public boolean isthread()
	{
		return true;
	}

	@Override
	public LuaThread optthread(LuaThread defval)
	{
		return this;
	}

	@Override
	public LuaThread checkthread()
	{
		return this;
	}

	@Override
	public LuaValue getmetatable()
	{
		return s_metatable;
	}

	@Override
	public LuaValue getfenv()
	{
		return _env;
	}

	@Override
	public void setfenv(LuaValue env)
	{
		_env = env;
	}

	public String getStatus()
	{
		return STATUS_NAMES[_state._status];
	}

	/**
	 * Get the currently running thread.
	 * @return {@link LuaThread} that is currenly running
	 */
	public static LuaThread getRunning()
	{
		return running_thread;
	}

	/**
	 * Test if this is the main thread
	 * @return true if this is the main thread
	 */
	public static boolean isMainThread(LuaThread r)
	{
		return r == main_thread;
	}

	/**
	 * Set the globals of the current thread.
	 * <p>
	 * This must be done once before any other code executes.
	 * @param globals The global variables for the main ghread.
	 */
	public static void setGlobals(LuaValue globals)
	{
		running_thread._env = globals;
	}

	/** Get the current thread's environment
	 * @return {@link LuaValue} containing the global variables of the current thread.
	 */
	public static LuaValue getGlobals()
	{
		LuaValue e = running_thread._env;
		return e != null ? e : LuaValue.error("LuaThread.setGlobals() not initialized");
	}

	/**
	 * Callback used at the beginning of a call to prepare for possible getfenv/setfenv calls
	 * @param function Function being called
	 * @return CallStack which is used to signal the return or a tail-call recursion
	 * @see LibDebug
	 */
	public static CallStack onCall(LuaFunction function)
	{
		CallStack cs = running_thread._callstack;
		cs.onCall(function);
		return cs;
	}

	/**
	 * Get the function called as a specific location on the stack.
	 * @param level 1 for the function calling this one, 2 for the next one.
	 * @return LuaFunction on the call stack, or null if outside of range of active stack
	 */
	public static LuaFunction getCallstackFunction(int level)
	{
		return running_thread._callstack.getFunction(level);
	}

	/**
	 * Replace the error function of the currently running thread.
	 * @param errfunc the new error function to use.
	 * @return the previous error function.
	 */
	public static LuaValue setErrorFunc(LuaValue errfunc)
	{
		LuaValue prev = running_thread._err;
		running_thread._err = errfunc;
		return prev;
	}

	/** Yield the current thread with arguments
	 *
	 * @param args The arguments to send as return values to {@link #resume(Varargs)}
	 * @return {@link Varargs} provided as arguments to {@link #resume(Varargs)}
	 */
	public static Varargs yield(Varargs args)
	{
		State s = running_thread._state;
		if(s._function == null)
		    throw new LuaError("cannot yield main thread");
		return s.lua_yield(args);
	}

	/** Start or resume this thread
	 *
	 * @param args The arguments to send as return values to {@link #yield(Varargs)}
	 * @return {@link Varargs} provided as arguments to {@link #yield(Varargs)}
	 */
	public Varargs resume(Varargs args)
	{
		if(_state._status > STATUS_SUSPENDED)
		    return LuaValue.varargsOf(LuaValue.FALSE,
		            LuaValue.valueOf("cannot resume " + LuaThread.STATUS_NAMES[_state._status] + " coroutine"));
		return _state.lua_resume(this, args);
	}

	private static class State implements Runnable
	{
		final WeakReference<LuaThread> _lua_thread;
		final LuaValue                 _function;
		Varargs                        _args   = LuaValue.NONE;
		Varargs                        _result = LuaValue.NONE;
		String                         _error;
		int                            _status = LuaThread.STATUS_INITIAL;

		private State(LuaThread lua_thread, LuaValue function)
		{
			_lua_thread = new WeakReference<LuaThread>(lua_thread);
			_function = function;
		}

		@Override
		public synchronized void run()
		{
			try
			{
				Varargs a = _args;
				_args = LuaValue.NONE;
				_result = _function.invoke(a);
			}
			catch(Throwable t)
			{
				_error = t.getMessage();
			}
			finally
			{
				_status = LuaThread.STATUS_DEAD;
				notify();
			}
		}

		private synchronized Varargs lua_resume(LuaThread new_thread, Varargs varargs)
		{
			LuaThread previous_thread = LuaThread.running_thread;
			try
			{
				LuaThread.running_thread = new_thread;
				_args = varargs;
				if(_status == STATUS_INITIAL)
				{
					_status = STATUS_RUNNING;
					new Thread(this, "Coroutine-" + coroutine_count.incrementAndGet()).start();
				}
				else
					notify();
				previous_thread._state._status = STATUS_NORMAL;
				_status = STATUS_RUNNING;
				wait();
				return (_error != null ?
				        LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf(_error)) :
				        LuaValue.varargsOf(LuaValue.TRUE, _result));
			}
			catch(InterruptedException ie)
			{
				throw new OrphanedThread();
			}
			finally
			{
				running_thread = previous_thread;
				running_thread._state._status = STATUS_RUNNING;
				_args = LuaValue.NONE;
				_result = LuaValue.NONE;
				_error = null;
			}
		}

		private synchronized Varargs lua_yield(Varargs varargs)
		{
			try
			{
				_result = varargs;
				_status = STATUS_SUSPENDED;
				notify();
				do
				{
					wait(thread_orphan_check_interval);
					if(_lua_thread.get() == null)
					{
						_status = STATUS_DEAD;
						throw new OrphanedThread();
					}
				}
				while(_status == STATUS_SUSPENDED);
				return _args;
			}
			catch(InterruptedException ie)
			{
				_status = STATUS_DEAD;
				throw new OrphanedThread();
			}
			finally
			{
				_args = LuaValue.NONE;
				_result = LuaValue.NONE;
			}
		}
	}

	public static final class CallStack
	{
		private final LuaFunction[] _functions = new LuaFunction[MAX_CALLSTACK];
		private int                 _calls;

		/**
		 * Method to indicate the start of a call
		 * @see LibDebug
		 */
		void onCall(LuaFunction function)
		{
			if(_calls >= 0 && _calls < MAX_CALLSTACK)
				_functions[_calls++] = function;
			if(LibDebug.DEBUG_ENABLED)
			    LibDebug.debugOnCall(running_thread, _calls, function);
		}

		/**
		 * Method to signal the end of a call
		 * @see LibDebug
		 */
		public void onReturn()
		{
			if(_calls > 0 && _calls <= MAX_CALLSTACK)
				_functions[--_calls] = null;
			if(LibDebug.DEBUG_ENABLED)
			    LibDebug.debugOnReturn(running_thread, _calls);
		}

		/**
		 * Get number of calls in stack
		 * @return number of calls in current call stack
		 * @see LibDebug
		 */
		public int getCallstackDepth()
		{
			return _calls;
		}

		/**
		 * Get the function at a particular level of the stack.
		 * @param level # of levels back from the top of the stack.
		 * @return LuaFunction, or null if beyond the stack limits.
		 */
		private LuaFunction getFunction(int level)
		{
			return level > 0 && level <= _calls ? _functions[_calls - level] : null;
		}
	}
}
