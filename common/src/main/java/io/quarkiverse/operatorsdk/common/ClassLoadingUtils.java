package io.quarkiverse.operatorsdk.common;

import java.lang.reflect.InvocationTargetException;

public class ClassLoadingUtils {

    private ClassLoadingUtils() {
    }

    @SuppressWarnings({ "unchecked", "unused" })
    public static <T> Class<T> loadClass(String className, Class<T> expected) {
        try {
            return (Class<T>) Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (Exception e) {
            throw new IllegalArgumentException("Couldn't find class " + className, e);
        }
    }

    public static <T> T instantiate(Class<T> toInstantiate) {
        try {
            final var constructor = toInstantiate.getConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException("Couldn't instantiate " + toInstantiate.getName(),
                    e);
        }
    }
}
