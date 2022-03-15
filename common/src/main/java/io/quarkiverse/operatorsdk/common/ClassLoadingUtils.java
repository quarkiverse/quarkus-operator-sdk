package io.quarkiverse.operatorsdk.common;

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
}
