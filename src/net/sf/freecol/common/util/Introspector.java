/**
 *  Copyright (C) 2002-2022   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.sf.freecol.common.util.StringUtils.*;


/**
 * A class to allow access to the methods "fooType getFoo()" and
 * "void setFoo(fooType)" conventionally seen in objects.
 * Useful when Foo arrives as a run-time String, such as is the
 * case in serialization to/from XML representations.
 */
public class Introspector {

    public static class IntrospectorException extends ReflectiveOperationException {
        public IntrospectorException(Throwable cause) {
            super(cause);
        }

        public IntrospectorException(String err, Throwable cause) {
            super(err, cause);
        }
    }
    
    /** The class whose field we are to operate on. */
    private final Class<?> theClass;

    /** The field whose get/set methods we wish to invoke. */
    private final String field;


    /**
     * Build a new Introspector for the specified class and field name.
     *
     * @param theClass The {@code Class} of interest.
     * @param field The field name within the class of interest.
     */
    public Introspector(Class<?> theClass, String field) {
        if (field == null || field.isEmpty()) {
            throw new RuntimeException("Field may not be empty: " + this);
        }
        this.theClass = theClass;
        this.field = field;
    }

    /**
     * Get a function that converts to String from a given class.
     * We use Enum.name() for enums, and String.valueOf(argType) for the rest.
     *
     * @param argType A {@code Class} to find a converter for.
     * @return A conversion function, or null on error.
     * @exception NoSuchMethodException if no converter is found.
     */
    public static String convertToString(Object arg) {
        final Class<?> argType = arg.getClass();
        if (argType.isEnum())
            return ((Enum<?>)arg).name();
        return String.valueOf(arg);
    }

    /**
     * Get a function that converts from String to a given class.
     * We use Enum.valueOf(Class, String) for enums, and
     * argType.valueOf(String) for the rest, having first dodged
     * the primitive types.
     *
     * @param argType A {@code Class} to find a converter for.
     * @return A conversion function, or null on error.
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertFromString(Class<T> argType, String arg) {
        if (argType == String.class)
            return (T)arg;
        if (argType.isEnum())
            return (T)Enum.valueOf((Class)argType, arg);
        if (argType == Integer.class) return (T)Integer.valueOf(arg);
        if (argType == Boolean.class) return (T)Boolean.valueOf(arg);
        if (argType == Float.class) return (T)Float.valueOf(arg);
        if (argType == Double.class) return (T)Double.valueOf(arg);
        if (argType == Character.class) return (T)Character.valueOf(arg.charAt(0));
        if (argType.isPrimitive()) {
            if (argType == Integer.TYPE) return (T)Integer.valueOf(arg);
            if (argType == Boolean.TYPE) return (T)Boolean.valueOf(arg);
            if (argType == Float.TYPE) return (T)Float.valueOf(arg);
            if (argType == Double.TYPE) return (T)Double.valueOf(arg);
            if (argType == Character.TYPE) return (T)Character.valueOf(arg.charAt(0));
            throw new IllegalArgumentException("Need compatible class for primitive " + argType.getName());
        }
        final Meta meta = IntrospectorImpl.metas.get(argType);
        try {
            return (T)meta.invokeMethod(null, "valueOf", arg);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Invoke the get-method for this Introspector.
     *
     * @param obj An {@code Object} (really of type theClass)
     *        whose get-method is to be invoked.
     * @return A {@code String} containing the result of invoking
     *         the get-method.
     * @exception IntrospectorException encompasses many failures.
     */
    public String getter(Object obj) throws IntrospectorException {
        final String methodName = "get" + capitalize(field);
        try {
            final Meta meta = IntrospectorImpl.metas.get(theClass);
            return convertToString(meta.invokeMethod(obj, methodName));
        } catch (Exception e) {
            throw new IntrospectorException(methodName, e);
        }
    }

    /**
     * Invoke the set-method provided by this Introspector.
     *
     * @param obj An {@code Object} (really of type theClass)
     *        whose set-method is to be invoked.
     * @param value A {@code String} containing the value to be set.
     * @exception IntrospectorException encompasses many failures.
     */
    public void setter(Object obj, String value) throws IntrospectorException {
        final String methodName = "set" + capitalize(field);
        try {
            final Meta meta = IntrospectorImpl.metas.get(theClass);
            final Class<?> fieldType = meta.invokeMethod(obj, "get" + capitalize(field)).getClass();
            meta.invokeMethod(obj, methodName, convertFromString(fieldType, value));
        } catch (Exception e) {
            throw new IntrospectorException(methodName, e);
        }
    }

    /**
     * Get a class by name.
     *
     * @param name The class name to look for.
     * @return The class found, or null if none available.
     */
    public static Class<?> getClassByName(String name) throws ClassNotFoundException {
        if (!name.startsWith(PACKAGE))
            throw new ClassNotFoundException(name);

        final Class<?> clazz = IntrospectorImpl.names.get(name.substring(PACKAGE.length()));
        if (clazz == null)
            throw new ClassNotFoundException(name);

        return clazz;
    }       

    /**
     * Constructs a new instance of an object of a class specified by name,
     * with supplied parameters.
     *
     * @param tag The name of the class to instantiate.
     * @param types The argument types of the constructor to call.
     * @param params The parameters to call the constructor with.
     * @return The new object instance.
     * @exception IntrospectorException wraps all exceptional conditions.
     */
    public static Object instantiate(String tag, Class<?>[] types,
                                     Object[] params)
        throws IntrospectorException {
        Class<?> messageClass;
        try {
            messageClass = getClassByName(tag);
        } catch (ClassNotFoundException ex) {
            throw new IntrospectorException("Unable to find class " + tag, ex);
        }
        return instantiate(messageClass, types, params);
    }

    /**
     * Constructs a new instance of an object of a class specified by name,
     * with supplied parameters.
     *
     * @param <T> The actual return type.
     * @param messageClass The class to instantiate.
     * @param types The argument types of the constructor to call.
     * @param params The parameters to call the constructor with.
     * @return The new instance.
     * @exception IntrospectorException wraps all exceptional conditions.
     */
    @SuppressWarnings("unchecked")
    public static <T> T instantiate(Class<T> messageClass, Class<?>[] types,
                                    Object[] params)
        throws IntrospectorException {
        try {
            final Meta meta = IntrospectorImpl.metas.get(messageClass);
            return (T)meta.newInstance(types, params);
        } catch (Exception ex) {
            throw new IntrospectorException("Failed to construct " + messageClass.getName(), ex);
        }
    }

    /**
     * Invoke an object method by name.
     *
     * @param <T> The actual return type.
     * @param object The base object.
     * @param methodName The name of the method to invoke.
     * @param returnClass The expected class to return.
     * @return The result of invoking the method.
     * @exception IllegalAccessException if the method exists but is hidden.
     * @exception InvocationTargetException if the target can not be invoked.
     * @exception NoSuchMethodException if the invocation fails.
     */
    public static <T> T invokeMethod(Object object, String methodName,
                                     Class<T> returnClass)
        throws IllegalAccessException, InvocationTargetException,
               NoSuchMethodException {
        try {
            final Meta meta = IntrospectorImpl.metas.get(object.getClass());
            return net.ringoz.GwtCompat.class_cast(returnClass, meta.invokeMethod(object, methodName));
        } catch (NoSuchMethodException|IllegalAccessException e) {
            throw e;
        } catch (Exception e) {
            throw new InvocationTargetException(e);
        }
    }

    /**
     * Codegen.
     */

    static final String PACKAGE = "net.sf.freecol.";

    static abstract class Meta {
        static boolean areSame(Object[] types, Object... other) {
            return Arrays.equals(types, other);
        }

        Object newInstance(Class<?>[] types, Object[] params) throws Exception {
            throw new InstantiationException();
        }

        Object invokeMethod(Object object, String method, Object... params) throws Exception {
            switch (method) {
                case "toString": return object.toString();
                case "hashCode": return object.hashCode();
                case "equals": return object.equals(params[0]);
                default: throw new NoSuchMethodException();
            }
        }
    }

    @net.ringoz.GwtIncompatible
    private static boolean inheritsMethod(Class<?> clazz, Method meth) {
        try {
            clazz.getSuperclass().getMethod(meth.getName(), meth.getParameterTypes());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @net.ringoz.GwtIncompatible
    private static final java.util.Set<Class<?>> emitted = new java.util.HashSet<>();

    @net.ringoz.GwtIncompatible
    private static void emitClass(PrintStream out, Class<?> clazz) {
        if (!Modifier.isPublic(clazz.getModifiers()))
            return;

        if (emitted.contains(clazz))
            return;

        out.println("// " + clazz.getCanonicalName());
        emitted.add(clazz);

        out.println("final Meta " + clazz.getName().substring(PACKAGE.length()).replace(".", "_") + " = new Meta() {");
        if (!Modifier.isAbstract(clazz.getModifiers())) {
            out.println(clazz.getCanonicalName() + " newInstance(Class<?>[] types, Object[] params) throws Exception {");
            for (Constructor<?> ctor : clazz.getConstructors()) {
                final var types = Arrays.asList(ctor.getParameterTypes());
                if (types.isEmpty())
                    out.println("  if (types.length == 0)");
                else
                    out.println("  if (areSame(types, " + String.join(", ", types.stream().map((Class<?> type) -> type.getCanonicalName() + ".class").toArray(String[]::new)) + "))");
                out.println("    return new " + clazz.getCanonicalName() + "(" + String.join(", ", types.stream().map((Class<?> type) -> "(" + type.getCanonicalName() + ")params[" + types.indexOf(type) + "]").toArray(String[]::new)) + ");");
            }
            out.println("  throw new IllegalArgumentException();");
            out.println("}");
        }

        final var methods = Arrays.stream(clazz.getDeclaredMethods()).filter((Method meth) -> {
            if (!Modifier.isPublic(meth.getModifiers()))
                return false;
            if (meth.getAnnotation(net.ringoz.GwtIncompatible.class) != null)
                return false;
            if (inheritsMethod(clazz, meth))
                return false;
            if (meth.getName().startsWith("set") && meth.getParameterCount() != 1)
                return false;
            if (!meth.getName().startsWith("set") && meth.getParameterCount() != 0)
                return false;
            return true;
        }).sorted(Comparator.comparing(Method::getName)).toArray(Method[]::new);

        out.println("Object invokeMethod(Object object, String method, Object... params) throws Exception {");
        if (methods.length != 0) {
            out.println("  switch (method) {");
            for (Method meth : methods) {
                final var types = Arrays.asList(meth.getParameterTypes());
                if (meth.getReturnType().equals(Void.TYPE))
                    out.println("  case \"" + meth.getName() + "\": ((" + clazz.getCanonicalName() + ")object)." + meth.getName() + "(" + String.join(", ", types.stream().map((Class<?> type) -> "(" + type.getCanonicalName() + ")params[" + types.indexOf(type) + "]").toArray(String[]::new)) + "); return null;");
                else
                    out.println("  case \"" + meth.getName() + "\": return ((" + clazz.getCanonicalName() + ")object)." + meth.getName() + "(" + String.join(", ", types.stream().map((Class<?> type) -> "(" + type.getCanonicalName() + ")params[" + types.indexOf(type) + "]").toArray(String[]::new)) + ");");
            }
            out.println("  }");
        }
        out.println("  return " + (emitted.contains(clazz.getSuperclass()) ? clazz.getSuperclass().getName().substring(PACKAGE.length()).replace(".", "_") : "super") + ".invokeMethod(object, method, params);");
        out.println("}");

        out.println("};");
        out.println("names.put(\"" + clazz.getName().substring(PACKAGE.length()) + "\", " + clazz.getCanonicalName() + ".class);");
        out.println("metas.put(" + clazz.getCanonicalName() + ".class, " + clazz.getName().substring(PACKAGE.length()).replace(".", "_") + ");");
    }

    @net.ringoz.GwtIncompatible
    private static void emitSubClasses(PrintStream out, Class<?> root, String packageName) throws IOException {
        final var srcList = root.getClassLoader().getResources(packageName.replace(".", "/"));
        while (srcList.hasMoreElements()) {
            File dirFile = new File(srcList.nextElement().getFile());
            for (File file : dirFile.listFiles()) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                try {
                    final Class<?> clazz = Class.forName(className);
                    if (!root.isAssignableFrom(clazz))
                        continue;

                    final var hierarchy = Stream.iterate(clazz, (cls) -> cls != root.getSuperclass(), (Class<?> cls) -> cls.getSuperclass()).collect(Collectors.toList());
                    Collections.reverse(hierarchy);
                    hierarchy.forEach((cls) -> emitClass(out, cls));
                }
                catch (ClassNotFoundException e) {
                    continue;
                }
            }
        }
    }

    @net.ringoz.GwtIncompatible
    public static void main(String[] args) throws Exception {
        emitted.clear();
        try (PrintStream out = new PrintStream(args[0])) {
            out.println("// generated by Introspector::main");
            out.println("package net.sf.freecol.common.util;");
            out.println("import net.sf.freecol.common.util.Introspector.Meta;");
            out.println("class IntrospectorImpl {");
            out.println("static final java.util.Map<String,Class<?>> names = new java.util.HashMap<>();");
            out.println("static final java.util.Map<Class<?>,Meta> metas = new java.util.HashMap<>();");
            out.println("static {");
            emitSubClasses(out, net.sf.freecol.common.networking.Message.class, "net.sf.freecol.common.networking");
            emitSubClasses(out, net.sf.freecol.common.model.FreeColObject.class, "net.sf.freecol.common.model");
            emitSubClasses(out, net.sf.freecol.common.model.FreeColObject.class, "net.sf.freecol.server.model");
            emitSubClasses(out, net.sf.freecol.common.model.FreeColObject.class, "net.sf.freecol.server.ai");
            out.println("}");
            out.println("}");
        }
    }
}
