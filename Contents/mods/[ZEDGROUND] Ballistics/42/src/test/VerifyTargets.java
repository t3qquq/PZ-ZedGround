import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Checks every @Patch advice in the built jar against the real game / Viewpoint classes without running
 * the game: the target class and method must exist, every @Argument index must be in range and
 * type-compatible, and @Return parameters must match the target's return type.
 *
 * Usage: java -cp <ZombieBuddy.jar>:<mod jar>:. VerifyTargets <mod jar> <projectzomboid.jar> <Viewpoint.jar>
 */
public class VerifyTargets {
    public static void main(String[] a) throws Exception {
        File modJar = new File(a[0]);
        URLClassLoader game = new URLClassLoader(new URL[]{new File(a[1]).toURI().toURL(), new File(a[2]).toURI().toURL()}, null);
        int problems = 0, checked = 0;
        List<Class<?>> patches = new ArrayList<>();
        try (JarFile jf = new JarFile(modJar)) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
                String n = e.nextElement().getName();
                if (!n.endsWith(".class") || !(n.startsWith("t3zedballistics/") && n.indexOf('/', 16) < 0)) continue;
                Class<?> c = Class.forName(n.replace('/', '.').replace(".class", ""), false, VerifyTargets.class.getClassLoader());
                if (c.getAnnotation(Patch.class) != null) patches.add(c);
            }
        }
        for (Class<?> pc : patches) {
            Patch p = pc.getAnnotation(Patch.class);
            Class<?> target;
            try {
                target = Class.forName(p.className(), false, game);
            } catch (ClassNotFoundException e) {
                System.out.println("MISSING CLASS  " + p.className());
                problems++;
                continue;
            }
            List<Method> candidates = new ArrayList<>();
            for (Method m : target.getDeclaredMethods()) if (m.getName().equals(p.methodName())) candidates.add(m);
            if (candidates.isEmpty()) {
                System.out.println("MISSING METHOD " + p.className() + "." + p.methodName());
                problems++;
                continue;
            }
            for (Method advice : pc.getDeclaredMethods()) {
                boolean enter = advice.getAnnotation(Patch.OnEnter.class) != null;
                boolean exit = advice.getAnnotation(Patch.OnExit.class) != null;
                if (!enter && !exit) continue;
                checked++;
                boolean anyOk = false;
                StringBuilder why = new StringBuilder();
                for (Method t : candidates) {
                    String r = compatible(advice, t, enter);
                    if (r == null) { anyOk = true; break; }
                    why.append("\n      vs ").append(t).append(": ").append(r);
                }
                if (enter && advice.getAnnotation(Patch.OnEnter.class).skipOn() && advice.getReturnType() != boolean.class) {
                    anyOk = false;
                    why.append("\n      skipOn advice must return boolean");
                }
                System.out.println((anyOk ? "ok    " : "BAD   ") + pc.getSimpleName() + "." + advice.getName() + " -> "
                    + p.className() + "." + p.methodName() + (anyOk ? "" : why));
                if (!anyOk) problems++;
            }
        }
        System.out.println(checked + " advice methods checked, " + problems + " problem(s)");
        System.exit(problems == 0 ? 0 : 1);
    }

    static String compatible(Method advice, Method target, boolean enter) {
        Parameter[] ps = advice.getParameters();
        Class<?>[] tp = target.getParameterTypes();
        for (Parameter p : ps) {
            for (Annotation an : p.getAnnotations()) {
                if (an instanceof Patch.Argument arg) {
                    if (arg.value() >= tp.length) return "argument " + arg.value() + " out of range (" + tp.length + " params)";
                    Class<?> want = p.getType();
                    if (want != Object.class && !box(want).isAssignableFrom(box(tp[arg.value()])))
                        return "argument " + arg.value() + " is " + tp[arg.value()].getName() + ", advice wants " + want.getName();
                } else if (an instanceof Patch.Return) {
                    if (enter) return "@Return on OnEnter";
                    Class<?> rt = target.getReturnType();
                    if (rt == void.class) return "@Return on void method";
                    if (p.getType() != Object.class && p.getType() != rt) return "return is " + rt.getName() + ", advice wants " + p.getType().getName();
                } else if (an instanceof Patch.This) {
                    if (java.lang.reflect.Modifier.isStatic(target.getModifiers())) return "@This on static method";
                }
            }
        }
        return null;
    }

    static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == float.class) return Float.class;
        if (c == boolean.class) return Boolean.class;
        if (c == double.class) return Double.class;
        if (c == long.class) return Long.class;
        if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class;
        if (c == char.class) return Character.class;
        return c;
    }
}
