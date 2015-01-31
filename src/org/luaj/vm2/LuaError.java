package org.luaj.vm2;

import org.luaj.vm2.lib.DebugLib;

/**
 * RuntimeException that is thrown and caught in response to a lua error.
 * <p>
 * {@link LuaError} is used wherever a lua call to {@code error()}
 * would be used within a script.
 * <p>
 * Since it is an unchecked exception inheriting from {@link RuntimeException},
 * Java method signatures do notdeclare this exception, althoug it can
 * be thrown on almost any luaj Java operation.
 * This is analagous to the fact that any lua script can throw a lua error at any time.
 * <p>
 */
public class LuaError extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	private String            traceback;

	/**
	 *  Run the error hook if there is one
	 *  @param msg the message to use in error hook processing.
	 * */
	private static String errorHook(String msg)
	{
		LuaThread thread = LuaThread.getRunning();
		if(thread.err != null)
		{
			LuaValue errfunc = thread.err;
			thread.err = null;
			try
			{
				return errfunc.call(LuaValue.valueOf(msg)).tojstring();
			}
			catch(Throwable t)
			{
				return "error in error handling";
			}
			finally
			{
				thread.err = errfunc;
			}
		}
		return msg;
	}

	private Throwable cause;

	/** Construct LuaError when a program exception occurs.
	 * <p>
	 * All errors generated from lua code should throw LuaError(String) instead.
	 * @param cause the Throwable that caused the error, if known.
	 */
	public LuaError(Throwable cause)
	{
		super(errorHook(addFileLine("vm error: " + cause)));
		this.cause = cause;
		this.traceback = DebugLib.traceback(1);
	}

	/**
	 * Construct a LuaError with a specific message.
	 *
	 * @param message message to supply
	 */
	public LuaError(String message)
	{
		super(errorHook(addFileLine(message)));
		this.traceback = DebugLib.traceback(1);
	}

	/**
	 * Construct a LuaError with a message, and level to draw line number information from.
	 * @param message message to supply
	 * @param level where to supply line info from in call stack
	 */
	public LuaError(String message, int level)
	{
		super(errorHook(addFileLine(message, level)));
		this.traceback = DebugLib.traceback(1);
	}

	/**
	 * Add file and line info to a message at a particular level
	 * @param message the String message to use
	 * @param level where to supply line info from in call stack
	 * */
	private static String addFileLine(String message, int level)
	{
		if(message == null) return null;
		if(level == 0) return message;
		String fileline = DebugLib.fileline(level - 1);
		return fileline != null ? fileline + ": " + message : message;
	}

	/** Add file and line info for the nearest enclosing closure
	 * @param message the String message to use
	 * */
	private static String addFileLine(String message)
	{
		if(message == null) return null;
		String fileline = DebugLib.fileline();
		return fileline != null ? fileline + ": " + message : message;
	}

	/** Print the message and stack trace */
	@Override
	public void printStackTrace()
	{
		System.out.println(toString());
		if(traceback != null)
		    System.out.println(traceback);
	}

	/**
	 * Get the cause, if any.
	 */
	@SuppressWarnings("sync-override")
	@Override
	public Throwable getCause()
	{
		return cause;
	}
}
