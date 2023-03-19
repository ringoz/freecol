package net.sf.freecol.common.util;

import net.sf.freecol.common.util.Introspector.Function;

public final class ReflectCompat {
    static Class<?> getClass(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    static Function getFunction(Object object, String method) {
        final var clazz = (object instanceof Class) ? (Class<?>)object : object.getClass();
        for (final var meth : clazz.getMethods()) {
            if (meth.getName().equals(method))
                return (self, params) -> meth.invoke(self, params);
        }
        return null;
    }
}
