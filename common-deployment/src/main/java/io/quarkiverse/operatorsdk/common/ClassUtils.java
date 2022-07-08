package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.CUSTOM_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.HAS_METADATA;
import static io.quarkiverse.operatorsdk.common.Constants.IGNORE_RECONCILER;
import static io.quarkiverse.operatorsdk.common.Constants.RECONCILER;

import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.stream.Stream;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.quarkus.deployment.util.JandexUtil;

public class ClassUtils {

    private ClassUtils() {
    }

    /**
     * Metadata about a processable reconciler implementation.
     */
    public static class ReconcilerInfo {
        private final ClassInfo classInfo;
        private final DotName primaryTypeName;
        private final String name;

        public ReconcilerInfo(ClassInfo classInfo, IndexView index) {
            this.classInfo = classInfo;
            final var primaryType = JandexUtil.resolveTypeParameters(classInfo.name(), RECONCILER, index)
                    .get(0);
            this.primaryTypeName = primaryType.name();
            this.name = ConfigurationUtils.getReconcilerName(classInfo);
        }

        public DotName primaryTypeName() {
            return primaryTypeName;
        }

        public String name() {
            return name;
        }

        public ClassInfo classInfo() {
            return classInfo;
        }
    }

    /**
     * Only retrieve {@link io.javaoperatorsdk.operator.api.reconciler.Reconciler} implementations that should be considered by
     * the extension, excluding the SDK's own implementations and non-processable (i.e. reconcilers that are not correctly
     * parameterized) ones.
     *
     * @param index the {@link IndexView} used to retrieve class informations
     * @param log a {@link Logger} used to output skipped reconcilers information
     * @return a stream of {@link ReconcilerInfo} providing information about processable reconcilers
     */
    public static Stream<ReconcilerInfo> getKnownReconcilers(IndexView index, Logger log) {
        return index.getAllKnownImplementors(RECONCILER).stream()
                .map(ci -> keep(ci, index, log))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private static Optional<ReconcilerInfo> keep(ClassInfo ci, IndexView index, Logger log) {
        final var consideredClassName = ci.name();
        if (Modifier.isAbstract(ci.flags())) {
            log.warnv("Skipping ''{0}'' reconciler because it''s abstract", consideredClassName);
            return Optional.empty();
        }

        // Ignore Reconciler implementations annotated with @Ignore
        if (ci.annotations().containsKey(IGNORE_RECONCILER)) {
            log.debugv("Skipping ''{0}'' reconciler because it''s annotated with @Ignore", consideredClassName);
            return Optional.empty();
        }

        ReconcilerInfo info = new ReconcilerInfo(ci, index);
        final var primaryTypeDN = info.primaryTypeName;
        // if we get CustomResource instead of a subclass, ignore the controller since we cannot do anything with it
        if (primaryTypeDN.toString() == null || CUSTOM_RESOURCE.equals(primaryTypeDN) || HAS_METADATA.equals(primaryTypeDN)) {
            log.warnv(
                    "Skipped processing of ''{0}'' reconciler as it''s not parameterized with a CustomResource or HasMetadata sub-class",
                    info.name);
            return Optional.empty();
        }

        return Optional.of(info);
    }
}
