package org.luaj.vm2.compiler;

class InstructionPtr
{
	final int[] code;
	final int   idx;

	InstructionPtr(int[] c, int i)
	{
		code = c;
		idx = i;
	}

	int get()
	{
		return code[idx];
	}

	void set(int value)
	{
		code[idx] = value;
	}
}
