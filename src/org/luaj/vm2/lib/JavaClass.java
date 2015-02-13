package org.luaj.vm2.lib;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;

/**
 * LuaValue that represents a Java class.
 * <p>
 * Will respond to get() and set() by returning field values, or java methods.
 * <p>
 * This class is not used directly.
 * It is returned by calls to {@link CoerceJavaToLua#coerce(Object)}
 * when a Class is supplied.
 * @see CoerceJavaToLua
 * @see CoerceLuaToJava
 */
final class JavaClass extends JavaInstance implements CoerceJavaToLua.Coercion
{
	private static final ConcurrentHashMap<Class<?>, JavaClass> classes = new ConcurrentHashMap<Class<?>, JavaClass>();

	static final LuaValue                                       NEW     = valueOf("new");

	HashMap<LuaString, Field>                                   _fields;
	HashMap<LuaValue, Object>                                   _methods;

	static JavaClass forClass(Class<?> c)
	{
		JavaClass j = classes.get(c);
		return j != null ? j : classes.putIfAbsent(c, new JavaClass(c));
	}

	JavaClass(Class<?> c)
	{
		super(c);
		_jclass = this;
	}

	@Override
	public LuaValue coerce(Object javaValue)
	{
		return this;
	}

	Field getField(LuaValue key)
	{
		if(_fields == null)
		{
			HashMap<LuaString, Field> m = new HashMap<LuaString, Field>();
			Field[] f = ((Class<?>)_instance).getFields();
			for(int i = 0; i < f.length; i++)
			{
				Field fi = f[i];
				if(Modifier.isPublic(fi.getModifiers()))
				{
					m.put(LuaValue.valueOf(fi.getName()), fi);
					try
					{
						if(!fi.isAccessible())
						    fi.setAccessible(true);
					}
					catch(SecurityException s)
					{
					}
				}
			}
			_fields = m;
		}
		return _fields.get(key);
	}

	LuaValue getMethod(LuaValue key)
	{
		if(_methods == null)
		{
			HashMap<String, List<JavaMethod>> namedlists = new HashMap<String, List<JavaMethod>>();
			Method[] m = ((Class<?>)_instance).getMethods();
			for(int i = 0; i < m.length; i++)
			{
				Method mi = m[i];
				if(Modifier.isPublic(mi.getModifiers()))
				{
					String name = mi.getName();
					List<JavaMethod> list = namedlists.get(name);
					if(list == null)
					    namedlists.put(name, list = new ArrayList<JavaMethod>());
					list.add(JavaMethod.forMethod(mi));
				}
			}
			HashMap<LuaValue, Object> map = new HashMap<LuaValue, Object>();
			Constructor<?>[] c = ((Class<?>)_instance).getConstructors();
			List<JavaConstructor> list = new ArrayList<JavaConstructor>();
			for(int i = 0; i < c.length; i++)
				if(Modifier.isPublic(c[i].getModifiers()))
				    list.add(JavaConstructor.forConstructor(c[i]));
			switch(list.size())
			{
				case 0:
					break;
				case 1:
					map.put(NEW, list.get(0));
					break;
				default:
					map.put(NEW, JavaConstructor.forConstructors(list.toArray(new JavaConstructor[list.size()])));
					break;
			}

			for(Iterator<?> it = namedlists.entrySet().iterator(); it.hasNext();)
			{
				Entry<?, ?> e = (Entry<?, ?>)it.next();
				String name = (String)e.getKey();
				List<?> methods = (List<?>)e.getValue();
				map.put(LuaValue.valueOf(name),
				        methods.size() == 1 ?
				                methods.get(0) :
				                JavaMethod.forMethods(methods.toArray(new JavaMethod[methods.size()])));
			}
			_methods = map;
		}
		return (LuaValue)_methods.get(key);
	}

	public LuaValue getConstructor()
	{
		return getMethod(NEW);
	}
}
