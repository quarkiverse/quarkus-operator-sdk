package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.CONFIGURED_CONTROLLER;
import static io.quarkiverse.operatorsdk.common.Constants.RESOURCE_CONTROLLER;

import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

public class ClassUtils {
    public static Class<?> loadClass(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (Exception e) {
            throw new IllegalArgumentException("Couldn't find class " + className, e);
        }
    }

    public static Stream<ClassInfo> getKnownResourceControllers(IndexView index, Logger log) {
        return index.getAllKnownImplementors(RESOURCE_CONTROLLER).stream()
                .filter(ci -> keep(ci, log));
    }

    private static boolean keep(ClassInfo ci, Logger log) {
        if (Modifier.isAbstract(ci.flags())) {
            log.debugv("Skipping ''{0}'' controller because it's abstract", ci.name());
            return false;
        }

        // Ignore ConfiguredController class
        return !ci.name().equals(CONFIGURED_CONTROLLER);
    }
}
