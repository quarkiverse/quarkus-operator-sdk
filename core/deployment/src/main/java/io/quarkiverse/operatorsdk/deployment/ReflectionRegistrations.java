package io.quarkiverse.operatorsdk.deployment;

import java.util.function.Predicate;

import org.jboss.jandex.DotName;

import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;

public class ReflectionRegistrations {

    private static final String[] PACKAGES_IGNORED_FOR_REFLECTION = {
            // Vert.x
            "io.vertx.core.",
            // OkHttp3
            "okhttp3.",
            // Okio
            "okio."
    };

    static final IgnoreTypeForReflectionPredicate IGNORE_TYPE_FOR_REFLECTION_PREDICATE = new IgnoreTypeForReflectionPredicate();

    private static class IgnoreTypeForReflectionPredicate implements Predicate<DotName> {

        @Override
        public boolean test(DotName dotName) {
            if (ReflectiveHierarchyBuildItem.DefaultIgnoreTypePredicate.INSTANCE.test(dotName)) {
                return true;
            }
            String name = dotName.toString();
            for (String packageName : PACKAGES_IGNORED_FOR_REFLECTION) {
                if (name.startsWith(packageName)) {
                    return true;
                }
            }
            return false;
        }
    }
}
