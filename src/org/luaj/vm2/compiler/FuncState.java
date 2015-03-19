package org.luaj.vm2.compiler;

import java.util.HashMap;
import org.luaj.vm2.LocVars;
import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaDouble;
import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.compiler.LexState.ConsControl;
import org.luaj.vm2.compiler.LexState.expdesc;

public final class FuncState extends LuaC
{
	static class UpValDesc
	{
		short k, info;
	}

	static class BlockCnt
	{
		BlockCnt previous;   /* chain */
		int      breaklist;  /* list of jumps out of this loop */
		short    nactvar;    /* # active locals outside the breakable structure */
		boolean  upval;      /* true if some variable in the block is an upvalue */
		boolean  isbreakable; /* true if `block' is a loop */
	}

	Prototype                  _f;                                        /* current function header */
	HashMap<LuaValue, Integer> htable;                                    /* table to find (and reuse) elements in `k' */
	FuncState                  prev;                                      /* enclosing function */
	LexState                   ls;                                        /* lexical state */
	LuaC                       L;                                         /* compiler being invoked */
	BlockCnt                   _bl;                                       /* chain of current blocks */
	int                        _pc;                                       /* next position to code (equivalent to `ncode') */
	int                        lasttarget;                                /* `pc' of last `jump target' */
	int                        _jpc;                                      /* list of pending jumps to `pc' */
	int                        freereg;                                   /* first free register */
	int                        nk;                                        /* number of elements in `k' */
	int                        np;                                        /* number of elements in `p' */
	short                      nlocvars;                                  /* number of elements in `locvars' */
	short                      nactvar;                                   /* number of active local variables */
	UpValDesc[]                upvalues = new UpValDesc[LUAI_MAXUPVALUES]; /* upvalues */
	short[]                    actvar   = new short[LUAI_MAXVARS];        /* declared-variable stack */

	FuncState()
	{
	}

	// =============================================================
	// from lcode.h
	// =============================================================

	InstructionPtr getcodePtr(expdesc e)
	{
		return new InstructionPtr(_f.code, e.u.s.info);
	}

	int getcode(expdesc e)
	{
		return _f.code[e.u.s.info];
	}

	int codeAsBx(int o, int A, int sBx)
	{
		return codeABx(o, A, sBx + MAXARG_sBx);
	}

	void setmultret(expdesc e)
	{
		setreturns(e, LUA_MULTRET);
	}

	// =============================================================
	// from lparser.c
	// =============================================================

	LocVars getlocvar(int i)
	{
		return _f.locvars[actvar[i]];
	}

	void checklimit(int v, int l, String msg)
	{
		if(v > l)
		    errorlimit(l, msg);
	}

	void errorlimit(int limit, String what)
	{
		String msg = (_f.linedefined == 0) ?
		        LuaC.pushfstring("main function has more than " + limit + " " + what) :
		        LuaC.pushfstring("function at line " + _f.linedefined + " has more than " + limit + " " + what);
		ls.lexerror(msg, 0);
	}

	int indexupvalue(LuaString name, expdesc v)
	{
		int i;
		for(i = 0; i < _f.nups; i++)
		{
			if(upvalues[i].k == v._k && upvalues[i].info == v.u.s.info)
			{
				_assert(_f.upvalues[i] == name);
				return i;
			}
		}
		/* new one */
		checklimit(_f.nups + 1, LUAI_MAXUPVALUES, "upvalues");
		if(_f.upvalues == null || _f.nups + 1 > _f.upvalues.length)
		    _f.upvalues = realloc(_f.upvalues, _f.nups * 2 + 1);
		_f.upvalues[_f.nups] = name;
		_assert(v._k == LexState.VLOCAL || v._k == LexState.VUPVAL);
		upvalues[_f.nups] = new UpValDesc();
		upvalues[_f.nups].k = (short)(v._k);
		upvalues[_f.nups].info = (short)(v.u.s.info);
		return _f.nups++;
	}

	int searchvar(LuaString n)
	{
		int i;
		for(i = nactvar - 1; i >= 0; i--)
		{
			if(n == getlocvar(i)._varname)
			    return i;
		}
		return -1; /* not found */
	}

	void markupval(int level)
	{
		BlockCnt bl1 = _bl;
		while(bl1 != null && bl1.nactvar > level)
			bl1 = bl1.previous;
		if(bl1 != null)
		    bl1.upval = true;
	}

	int singlevaraux(LuaString n, expdesc var, int base)
	{
		int v = searchvar(n); /* look up at current level */
		if(v >= 0)
		{
			var.init(LexState.VLOCAL, v);
			if(base == 0)
			    markupval(v); /* local will be used as an upval */
			return LexState.VLOCAL;
		}
		/* not found at current level; try upper one */
		if(prev == null)
		{ /* no more levels? */
			/* default is global variable */
			var.init(LexState.VGLOBAL, NO_REG);
			return LexState.VGLOBAL;
		}
		if(prev.singlevaraux(n, var, 0) == LexState.VGLOBAL)
		    return LexState.VGLOBAL;
		var.u.s.info = indexupvalue(n, var); /* else was LOCAL or UPVAL */
		var._k = LexState.VUPVAL; /* upvalue in this level */
		return LexState.VUPVAL;
	}

	void enterblock(BlockCnt bl1, boolean isbreakable)
	{
		bl1.breaklist = LexState.NO_JUMP;
		bl1.isbreakable = isbreakable;
		bl1.nactvar = nactvar;
		bl1.upval = false;
		bl1.previous = _bl;
		_bl = bl1;
		_assert(freereg == nactvar);
	}

	void leaveblock()
	{
		BlockCnt bl = _bl;
		_bl = bl.previous;
		ls.removevars(bl.nactvar);
		if(bl.upval)
		    codeABC(OP_CLOSE, bl.nactvar, 0, 0);
		/* a block either controls scope or breaks (never both) */
		_assert(!bl.isbreakable || !bl.upval);
		_assert(bl.nactvar == nactvar);
		freereg = nactvar; /* free registers */
		patchtohere(bl.breaklist);
	}

	void closelistfield(ConsControl cc)
	{
		if(cc.v._k == LexState.VVOID)
		    return; /* there is no list item */
		exp2nextreg(cc.v);
		cc.v._k = LexState.VVOID;
		if(cc.tostore == LFIELDS_PER_FLUSH)
		{
			setlist(cc.t.u.s.info, cc.na, cc.tostore); /* flush */
			cc.tostore = 0; /* no more items pending */
		}
	}

	static boolean hasmultret(int k)
	{
		return ((k) == LexState.VCALL || (k) == LexState.VVARARG);
	}

	void lastlistfield(ConsControl cc)
	{
		if(cc.tostore == 0) return;
		if(hasmultret(cc.v._k))
		{
			setmultret(cc.v);
			setlist(cc.t.u.s.info, cc.na, LUA_MULTRET);
			cc.na--;
			/** do not count last expression (unknown number of elements) */
		}
		else
		{
			if(cc.v._k != LexState.VVOID)
			    exp2nextreg(cc.v);
			setlist(cc.t.u.s.info, cc.na, cc.tostore);
		}
	}

	// =============================================================
	// from lcode.c
	// =============================================================

	void nil(int from, int n)
	{
		InstructionPtr previous;
		if(_pc > lasttarget)
		{ /* no jumps to current position? */
			if(_pc == 0)
			{ /* function start? */
				if(from >= nactvar)
				    return; /* positions are already clean */
			}
			else
			{
				previous = new InstructionPtr(_f.code, _pc - 1);
				if(GET_OPCODE(previous.get()) == OP_LOADNIL)
				{
					int pfrom = GETARG_A(previous.get());
					int pto = GETARG_B(previous.get());
					if(pfrom <= from && from <= pto + 1)
					{ /* can connect both? */
						if(from + n - 1 > pto)
						    SETARG_B(previous, from + n - 1);
						return;
					}
				}
			}
		}
		/* else no optimization */
		codeABC(OP_LOADNIL, from, from + n - 1, 0);
	}

	int jump()
	{
		int jpc = _jpc; /* save list of jumps to here */
		_jpc = LexState.NO_JUMP;
		int j = codeAsBx(OP_JMP, 0, LexState.NO_JUMP);
		j = concat(j, jpc); /* keep them on hold */
		return j;
	}

	void ret(int first, int nret)
	{
		codeABC(OP_RETURN, first, nret + 1, 0);
	}

	int condjump(int /* OpCode */op, int A, int B, int C)
	{
		codeABC(op, A, B, C);
		return jump();
	}

	void fixjump(int pc, int dest)
	{
		InstructionPtr jmp = new InstructionPtr(_f.code, pc);
		int offset = dest - (pc + 1);
		_assert(dest != LexState.NO_JUMP);
		if(Math.abs(offset) > MAXARG_sBx)
		    ls.syntaxerror("control structure too long");
		SETARG_sBx(jmp, offset);
	}

	/*
	 * * returns current `pc' and marks it as a jump target (to avoid wrong *
	 * optimizations with consecutive instructions not in the same basic block).
	 */
	int getlabel()
	{
		return lasttarget = _pc;
	}

	int getjump(int pc)
	{
		int offset = GETARG_sBx(_f.code[pc]);
		/* point to itself represents end of list */
		if(offset == LexState.NO_JUMP)
		    /* end of list */
		    return LexState.NO_JUMP;
		/* turn offset into absolute position */
		return pc + 1 + offset;
	}

	InstructionPtr getjumpcontrol(int pc1)
	{
		InstructionPtr pi = new InstructionPtr(_f.code, pc1);
		if(pc1 >= 1 && testTMode(GET_OPCODE(pi.code[pi.idx - 1])))
		    return new InstructionPtr(pi.code, pi.idx - 1);
		return pi;
	}

	/*
	 * * check whether list has any jump that do not produce a value * (or
	 * produce an inverted value)
	 */
	boolean need_value(int list)
	{
		for(; list != LexState.NO_JUMP; list = getjump(list))
		{
			int i = getjumpcontrol(list).get();
			if(GET_OPCODE(i) != OP_TESTSET)
			    return true;
		}
		return false; /* not found */
	}

	boolean patchtestreg(int node, int reg)
	{
		InstructionPtr i = getjumpcontrol(node);
		if(GET_OPCODE(i.get()) != OP_TESTSET)
		    /* cannot patch other instructions */
		    return false;
		if(reg != NO_REG && reg != GETARG_B(i.get()))
			SETARG_A(i, reg);
		else
			/* no register to put value or register already has the value */
			i.set(CREATE_ABC(OP_TEST, GETARG_B(i.get()), 0, Lua.GETARG_C(i.get())));

		return true;
	}

	void removevalues(int list)
	{
		for(; list != LexState.NO_JUMP; list = getjump(list))
			patchtestreg(list, NO_REG);
	}

	void patchlistaux(int list, int vtarget, int reg, int dtarget)
	{
		while(list != LexState.NO_JUMP)
		{
			int next = getjump(list);
			if(patchtestreg(list, reg))
				fixjump(list, vtarget);
			else
				fixjump(list, dtarget); /* jump to default target */
			list = next;
		}
	}

	void dischargejpc()
	{
		patchlistaux(_jpc, _pc, NO_REG, _pc);
		_jpc = LexState.NO_JUMP;
	}

	void patchlist(int list, int target)
	{
		if(target == _pc)
			patchtohere(list);
		else
		{
			_assert(target < _pc);
			patchlistaux(list, target, NO_REG, target);
		}
	}

	void patchtohere(int list)
	{
		getlabel();
		_jpc = concat(_jpc, list);
	}

	int concat(int l1, int l2)
	{
		if(l2 == LexState.NO_JUMP)
		    return l1;
		if(l1 == LexState.NO_JUMP)
			l1 = l2;
		else
		{
			int list = l1;
			int next;
			while((next = getjump(list)) != LexState.NO_JUMP)
				/* find last element */
				list = next;
			fixjump(list, l2);
		}
		return l1;
	}

	void checkstack(int n)
	{
		int newstack = freereg + n;
		if(newstack > _f.maxstacksize)
		{
			if(newstack >= MAXSTACK)
			    ls.syntaxerror("function or expression too complex");
			_f.maxstacksize = newstack;
		}
	}

	void reserveregs(int n)
	{
		checkstack(n);
		freereg += n;
	}

	void freereg(int reg)
	{
		if(!ISK(reg) && reg >= nactvar)
		{
			freereg--;
			_assert(reg == freereg);
		}
	}

	void freeexp(expdesc e)
	{
		if(e._k == LexState.VNONRELOC)
		    freereg(e.u.s.info);
	}

	int addk(LuaValue v)
	{
		int idx;
		if(htable.containsKey(v))
		{
			idx = htable.get(v).intValue();
		}
		else
		{
			idx = nk;
			htable.put(v, new Integer(idx));
			Prototype f = _f;
			if(f.k == null || nk + 1 >= f.k.length)
			    f.k = realloc(f.k, nk * 2 + 1);
			f.k[nk++] = v;
		}
		return idx;
	}

	int stringK(LuaString s)
	{
		return addk(s);
	}

	int numberK(LuaValue r)
	{
		if(r instanceof LuaDouble)
		{
			double d = r.todouble();
			int i = (int)d;
			if(d == i)
			    r = LuaInteger.valueOf(i);
		}
		return addk(r);
	}

	int boolK(boolean b)
	{
		return addk((b ? LuaValue.TRUE : LuaValue.FALSE));
	}

	int nilK()
	{
		return addk(LuaValue.NIL);
	}

	void setreturns(expdesc e, int nresults)
	{
		if(e._k == LexState.VCALL)
		{ /* expression is an open function call? */
			SETARG_C(getcodePtr(e), nresults + 1);
		}
		else if(e._k == LexState.VVARARG)
		{
			SETARG_B(getcodePtr(e), nresults + 1);
			SETARG_A(getcodePtr(e), freereg);
			reserveregs(1);
		}
	}

	void setoneret(expdesc e)
	{
		if(e._k == LexState.VCALL)
		{ /* expression is an open function call? */
			e._k = LexState.VNONRELOC;
			e.u.s.info = GETARG_A(getcode(e));
		}
		else if(e._k == LexState.VVARARG)
		{
			SETARG_B(getcodePtr(e), 2);
			e._k = LexState.VRELOCABLE; /* can relocate its simple result */
		}
	}

	void dischargevars(expdesc e)
	{
		switch(e._k)
		{
			case LexState.VLOCAL:
			{
				e._k = LexState.VNONRELOC;
				break;
			}
			case LexState.VUPVAL:
			{
				e.u.s.info = codeABC(OP_GETUPVAL, 0, e.u.s.info, 0);
				e._k = LexState.VRELOCABLE;
				break;
			}
			case LexState.VGLOBAL:
			{
				e.u.s.info = codeABx(OP_GETGLOBAL, 0, e.u.s.info);
				e._k = LexState.VRELOCABLE;
				break;
			}
			case LexState.VINDEXED:
			{
				freereg(e.u.s.aux);
				freereg(e.u.s.info);
				e.u.s.info = this
				        .codeABC(OP_GETTABLE, 0, e.u.s.info, e.u.s.aux);
				e._k = LexState.VRELOCABLE;
				break;
			}
			case LexState.VVARARG:
			case LexState.VCALL:
			{
				setoneret(e);
				break;
			}
			default:
				break; /* there is one value available (somewhere) */
		}
	}

	int code_label(int A, int b, int jump)
	{
		getlabel(); /* those instructions may be jump targets */
		return codeABC(OP_LOADBOOL, A, b, jump);
	}

	void discharge2reg(expdesc e, int reg)
	{
		dischargevars(e);
		switch(e._k)
		{
			case LexState.VNIL:
			{
				nil(reg, 1);
				break;
			}
			case LexState.VFALSE:
			case LexState.VTRUE:
			{
				codeABC(OP_LOADBOOL, reg, (e._k == LexState.VTRUE ? 1 : 0),
				        0);
				break;
			}
			case LexState.VK:
			{
				codeABx(OP_LOADK, reg, e.u.s.info);
				break;
			}
			case LexState.VKNUM:
			{
				codeABx(OP_LOADK, reg, numberK(e.u.nval()));
				break;
			}
			case LexState.VRELOCABLE:
			{
				InstructionPtr pc = getcodePtr(e);
				SETARG_A(pc, reg);
				break;
			}
			case LexState.VNONRELOC:
			{
				if(reg != e.u.s.info)
				    codeABC(OP_MOVE, reg, e.u.s.info, 0);
				break;
			}
			default:
			{
				_assert(e._k == LexState.VVOID || e._k == LexState.VJMP);
				return; /* nothing to do... */
			}
		}
		e.u.s.info = reg;
		e._k = LexState.VNONRELOC;
	}

	void discharge2anyreg(expdesc e)
	{
		if(e._k != LexState.VNONRELOC)
		{
			reserveregs(1);
			discharge2reg(e, freereg - 1);
		}
	}

	void exp2reg(expdesc e, int reg)
	{
		discharge2reg(e, reg);
		if(e._k == LexState.VJMP)
		    e.t = concat(e.t, e.u.s.info); /* put this jump in `t' list */
		if(e.hasjumps())
		{
			int _final; /* position after whole expression */
			int p_f = LexState.NO_JUMP; /* position of an eventual LOAD false */
			int p_t = LexState.NO_JUMP; /* position of an eventual LOAD true */
			if(need_value(e.t) || need_value(e.f))
			{
				int fj = (e._k == LexState.VJMP) ? LexState.NO_JUMP : this
				        .jump();
				p_f = code_label(reg, 0, 1);
				p_t = code_label(reg, 1, 0);
				patchtohere(fj);
			}
			_final = getlabel();
			patchlistaux(e.f, _final, reg, p_f);
			patchlistaux(e.t, _final, reg, p_t);
		}
		e.f = e.t = LexState.NO_JUMP;
		e.u.s.info = reg;
		e._k = LexState.VNONRELOC;
	}

	void exp2nextreg(expdesc e)
	{
		dischargevars(e);
		freeexp(e);
		reserveregs(1);
		exp2reg(e, freereg - 1);
	}

	int exp2anyreg(expdesc e)
	{
		dischargevars(e);
		if(e._k == LexState.VNONRELOC)
		{
			if(!e.hasjumps())
			    return e.u.s.info; /* exp is already in a register */
			if(e.u.s.info >= nactvar)
			{ /* reg. is not a local? */
				exp2reg(e, e.u.s.info); /* put value on it */
				return e.u.s.info;
			}
		}
		exp2nextreg(e); /* default */
		return e.u.s.info;
	}

	void exp2val(expdesc e)
	{
		if(e.hasjumps())
			exp2anyreg(e);
		else
			dischargevars(e);
	}

	int exp2RK(expdesc e)
	{
		exp2val(e);
		switch(e._k)
		{
			case LexState.VKNUM:
			case LexState.VTRUE:
			case LexState.VFALSE:
			case LexState.VNIL:
			{
				if(nk <= MAXINDEXRK)
				{ /* constant fit in RK operand? */
					e.u.s.info = (e._k == LexState.VNIL) ? nilK()
					        : (e._k == LexState.VKNUM) ? numberK(e.u.nval())
					                : boolK((e._k == LexState.VTRUE));
					e._k = LexState.VK;
					return RKASK(e.u.s.info);
				}
				break;
			}
			case LexState.VK:
			{
				if(e.u.s.info <= MAXINDEXRK) /* constant fit in argC? */
				    return RKASK(e.u.s.info);
				break;
			}
		}
		/* not a constant in the right range: put it in a register */
		return exp2anyreg(e);
	}

	void storevar(expdesc var, expdesc ex)
	{
		switch(var._k)
		{
			case LexState.VLOCAL:
			{
				freeexp(ex);
				exp2reg(ex, var.u.s.info);
				return;
			}
			case LexState.VUPVAL:
			{
				int e = exp2anyreg(ex);
				codeABC(OP_SETUPVAL, e, var.u.s.info, 0);
				break;
			}
			case LexState.VGLOBAL:
			{
				int e = exp2anyreg(ex);
				codeABx(OP_SETGLOBAL, e, var.u.s.info);
				break;
			}
			case LexState.VINDEXED:
			{
				int e = exp2RK(ex);
				codeABC(OP_SETTABLE, var.u.s.info, var.u.s.aux, e);
				break;
			}
			default:
			{
				_assert(false); /* invalid var kind to store */
				break;
			}
		}
		freeexp(ex);
	}

	void self(expdesc e, expdesc key)
	{
		int func;
		exp2anyreg(e);
		freeexp(e);
		func = freereg;
		reserveregs(2);
		codeABC(OP_SELF, func, e.u.s.info, exp2RK(key));
		freeexp(key);
		e.u.s.info = func;
		e._k = LexState.VNONRELOC;
	}

	void invertjump(expdesc e)
	{
		InstructionPtr pc = getjumpcontrol(e.u.s.info);
		_assert(testTMode(GET_OPCODE(pc.get())) && GET_OPCODE(pc.get()) != OP_TESTSET && Lua.GET_OPCODE(pc.get()) != OP_TEST);
		// SETARG_A(pc, !(GETARG_A(pc.get())));
		int a = GETARG_A(pc.get());
		int nota = (a != 0 ? 0 : 1);
		SETARG_A(pc, nota);
	}

	int jumponcond(expdesc e, int cond)
	{
		if(e._k == LexState.VRELOCABLE)
		{
			int ie = getcode(e);
			if(GET_OPCODE(ie) == OP_NOT)
			{
				_pc--; /* remove previous OP_NOT */
				return condjump(OP_TEST, GETARG_B(ie), 0, (cond != 0 ? 0 : 1));
			}
			/* else go through */
		}
		discharge2anyreg(e);
		freeexp(e);
		return condjump(OP_TESTSET, NO_REG, e.u.s.info, cond);
	}

	void goiftrue(expdesc e)
	{
		int pc1; /* pc of last jump */
		dischargevars(e);
		switch(e._k)
		{
			case LexState.VK:
			case LexState.VKNUM:
			case LexState.VTRUE:
			{
				pc1 = LexState.NO_JUMP; /* always true; do nothing */
				break;
			}
			case LexState.VFALSE:
			{
				pc1 = jump(); /* always jump */
				break;
			}
			case LexState.VJMP:
			{
				invertjump(e);
				pc1 = e.u.s.info;
				break;
			}
			default:
			{
				pc1 = jumponcond(e, 0);
				break;
			}
		}
		e.f = concat(e.f, pc1); /* insert last jump in `f' list */
		patchtohere(e.t);
		e.t = LexState.NO_JUMP;
	}

	void goiffalse(expdesc e)
	{
		int pc1; /* pc of last jump */
		dischargevars(e);
		switch(e._k)
		{
			case LexState.VNIL:
			case LexState.VFALSE:
			{
				pc1 = LexState.NO_JUMP; /* always false; do nothing */
				break;
			}
			case LexState.VTRUE:
			{
				pc1 = jump(); /* always jump */
				break;
			}
			case LexState.VJMP:
			{
				pc1 = e.u.s.info;
				break;
			}
			default:
			{
				pc1 = jumponcond(e, 1);
				break;
			}
		}
		e.t = concat(e.t, pc1); /* insert last jump in `t' list */
		patchtohere(e.f);
		e.f = LexState.NO_JUMP;
	}

	void codenot(expdesc e)
	{
		dischargevars(e);
		switch(e._k)
		{
			case LexState.VNIL:
			case LexState.VFALSE:
			{
				e._k = LexState.VTRUE;
				break;
			}
			case LexState.VK:
			case LexState.VKNUM:
			case LexState.VTRUE:
			{
				e._k = LexState.VFALSE;
				break;
			}
			case LexState.VJMP:
			{
				invertjump(e);
				break;
			}
			case LexState.VRELOCABLE:
			case LexState.VNONRELOC:
			{
				discharge2anyreg(e);
				freeexp(e);
				e.u.s.info = codeABC(OP_NOT, 0, e.u.s.info, 0);
				e._k = LexState.VRELOCABLE;
				break;
			}
			default:
			{
				_assert(false); /* cannot happen */
				break;
			}
		}
		/* interchange true and false lists */
		{
			int temp = e.f;
			e.f = e.t;
			e.t = temp;
		}
		removevalues(e.f);
		removevalues(e.t);
	}

	void indexed(expdesc t, expdesc k)
	{
		t.u.s.aux = exp2RK(k);
		t._k = LexState.VINDEXED;
	}

	@SuppressWarnings("null")
	static boolean constfolding(int op, expdesc e1, expdesc e2)
	{
		LuaValue v1, v2, r;
		if(!e1.isnumeral() || !e2.isnumeral())
		    return false;
		v1 = e1.u.nval();
		v2 = e2.u.nval();
		switch(op)
		{
			case OP_ADD:
				r = v1.add(v2);
				break;
			case OP_SUB:
				r = v1.sub(v2);
				break;
			case OP_MUL:
				r = v1.mul(v2);
				break;
			case OP_DIV:
				r = v1.div(v2);
				break;
			case OP_MOD:
				r = v1.mod(v2);
				break;
			case OP_POW:
				r = v1.pow(v2);
				break;
			case OP_UNM:
				r = v1.neg();
				break;
			case OP_LEN:
				// r = v1.len();
				// break;
				return false; /* no constant folding for 'len' */
			default:
				_assert(false);
				r = null;
				break;
		}
		if(Double.isNaN(r.todouble()))
		    return false; /* do not attempt to produce NaN */
		e1.u.setNval(r);
		return true;
	}

	void codearith(int op, expdesc e1, expdesc e2)
	{
		if(constfolding(op, e1, e2))
		    return;
		int o2 = (op != OP_UNM && op != OP_LEN) ? exp2RK(e2) : 0;
		int o1 = exp2RK(e1);
		if(o1 > o2)
		{
			freeexp(e1);
			freeexp(e2);
		}
		else
		{
			freeexp(e2);
			freeexp(e1);
		}
		e1.u.s.info = codeABC(op, 0, o1, o2);
		e1._k = LexState.VRELOCABLE;
	}

	void codecomp(int /* OpCode */op, int cond, expdesc e1, expdesc e2)
	{
		int o1 = exp2RK(e1);
		int o2 = exp2RK(e2);
		freeexp(e2);
		freeexp(e1);
		if(cond == 0 && op != OP_EQ)
		{
			int temp; /* exchange args to replace by `<' or `<=' */
			temp = o1;
			o1 = o2;
			o2 = temp; /* o1 <==> o2 */
			cond = 1;
		}
		e1.u.s.info = condjump(op, cond, o1, o2);
		e1._k = LexState.VJMP;
	}

	void prefix(int /* UnOpr */op, expdesc e)
	{
		expdesc e2 = new expdesc();
		e2.init(LexState.VKNUM, 0);
		switch(op)
		{
			case LexState.OPR_MINUS:
			{
				if(e._k == LexState.VK)
				    exp2anyreg(e); /* cannot operate on non-numeric constants */
				codearith(OP_UNM, e, e2);
				break;
			}
			case LexState.OPR_NOT:
				codenot(e);
				break;
			case LexState.OPR_LEN:
			{
				exp2anyreg(e); /* cannot operate on constants */
				codearith(OP_LEN, e, e2);
				break;
			}
			default:
				_assert(false);
		}
	}

	void infix(int /* BinOpr */op, expdesc v)
	{
		switch(op)
		{
			case LexState.OPR_AND:
			{
				goiftrue(v);
				break;
			}
			case LexState.OPR_OR:
			{
				goiffalse(v);
				break;
			}
			case LexState.OPR_CONCAT:
			{
				exp2nextreg(v); /* operand must be on the `stack' */
				break;
			}
			case LexState.OPR_ADD:
			case LexState.OPR_SUB:
			case LexState.OPR_MUL:
			case LexState.OPR_DIV:
			case LexState.OPR_MOD:
			case LexState.OPR_POW:
			{
				if(!v.isnumeral())
				    exp2RK(v);
				break;
			}
			default:
			{
				exp2RK(v);
				break;
			}
		}
	}

	void posfix(int op, expdesc e1, expdesc e2)
	{
		switch(op)
		{
			case LexState.OPR_AND:
			{
				_assert(e1.t == LexState.NO_JUMP); /* list must be closed */
				dischargevars(e2);
				e2.f = concat(e2.f, e1.f);
				// *e1 = *e2;
				e1.setvalue(e2);
				break;
			}
			case LexState.OPR_OR:
			{
				_assert(e1.f == LexState.NO_JUMP); /* list must be closed */
				dischargevars(e2);
				e2.t = concat(e2.t, e1.t);
				// *e1 = *e2;
				e1.setvalue(e2);
				break;
			}
			case LexState.OPR_CONCAT:
			{
				exp2val(e2);
				if(e2._k == LexState.VRELOCABLE
				        && GET_OPCODE(getcode(e2)) == OP_CONCAT)
				{
					_assert(e1.u.s.info == GETARG_B(getcode(e2)) - 1);
					freeexp(e1);
					SETARG_B(getcodePtr(e2), e1.u.s.info);
					e1._k = LexState.VRELOCABLE;
					e1.u.s.info = e2.u.s.info;
				}
				else
				{
					exp2nextreg(e2); /* operand must be on the 'stack' */
					codearith(OP_CONCAT, e1, e2);
				}
				break;
			}
			case LexState.OPR_ADD:
				codearith(OP_ADD, e1, e2);
				break;
			case LexState.OPR_SUB:
				codearith(OP_SUB, e1, e2);
				break;
			case LexState.OPR_MUL:
				codearith(OP_MUL, e1, e2);
				break;
			case LexState.OPR_DIV:
				codearith(OP_DIV, e1, e2);
				break;
			case LexState.OPR_MOD:
				codearith(OP_MOD, e1, e2);
				break;
			case LexState.OPR_POW:
				codearith(OP_POW, e1, e2);
				break;
			case LexState.OPR_EQ:
				codecomp(OP_EQ, 1, e1, e2);
				break;
			case LexState.OPR_NE:
				codecomp(OP_EQ, 0, e1, e2);
				break;
			case LexState.OPR_LT:
				codecomp(OP_LT, 1, e1, e2);
				break;
			case LexState.OPR_LE:
				codecomp(OP_LE, 1, e1, e2);
				break;
			case LexState.OPR_GT:
				codecomp(OP_LT, 0, e1, e2);
				break;
			case LexState.OPR_GE:
				codecomp(OP_LE, 0, e1, e2);
				break;
			default:
				_assert(false);
		}
	}

	void fixline(int line)
	{
		_f.lineinfo[_pc - 1] = line;
	}

	int code(int instruction, int line)
	{
		dischargejpc(); /* `pc' will change */
		/* put new instruction in code array */
		if(_f.code == null || _pc + 1 > _f.code.length)
		    _f.code = LuaC.realloc(_f.code, _pc * 2 + 1);
		_f.code[_pc] = instruction;
		/* save corresponding line information */
		if(_f.lineinfo == null || _pc + 1 > _f.lineinfo.length)
		    _f.lineinfo = LuaC.realloc(_f.lineinfo,
		            _pc * 2 + 1);
		_f.lineinfo[_pc] = line;
		return _pc++;
	}

	int codeABC(int o, int a, int b, int c)
	{
		_assert(getOpMode(o) == iABC);
		_assert(getBMode(o) != OpArgN || b == 0);
		_assert(getCMode(o) != OpArgN || c == 0);
		return code(CREATE_ABC(o, a, b, c), ls._lastline);
	}

	int codeABx(int o, int a, int bc)
	{
		_assert(getOpMode(o) == iABx || getOpMode(o) == iAsBx);
		_assert(getCMode(o) == OpArgN);
		return code(CREATE_ABx(o, a, bc), ls._lastline);
	}

	void setlist(int base, int nelems, int tostore)
	{
		int c = (nelems - 1) / LFIELDS_PER_FLUSH + 1;
		int b = (tostore == LUA_MULTRET) ? 0 : tostore;
		_assert(tostore != 0);
		if(c <= MAXARG_C)
			codeABC(OP_SETLIST, base, b, c);
		else
		{
			codeABC(OP_SETLIST, base, b, 0);
			code(c, ls._lastline);
		}
		freereg = base + 1; /* free registers with list values */
	}
}
