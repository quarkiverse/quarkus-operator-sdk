package io.quarkiverse.operatorsdk.common;

import java.lang.reflect.InvocationTargetException;

public class ClassLoadingUtils {

    private ClassLoadingUtils() {
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> loadClass(String className, Class<T> expected) {
        try {
            return (Class<T>) Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (Exception e) {
            throw new IllegalArgumentException("Couldn't find class " + className, e);
        }
    }

    public static <T> T instantiate(Class<T> toInstantiate) {
        try {
            return toInstantiate.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException("Couldn't instantiate " + toInstantiate.getName(),
                    e);
        }
    }
}
