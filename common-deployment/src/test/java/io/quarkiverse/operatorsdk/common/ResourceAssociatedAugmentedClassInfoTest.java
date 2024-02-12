package io.quarkiverse.operatorsdk.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.AbstractMap.SimpleEntry;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkus.test.common.TestClassIndexer;

class ResourceAssociatedAugmentedClassInfoTest {

    @Test
    void getClassNamesToRegisterForReflection() {
        final var indexAndInfo = create(ConfigMapReconciler.class);
        final var index = indexAndInfo.getKey();
        final var info = indexAndInfo.getValue();
        info.augmentIfKept(index, null, null);

        assertEquals(List.of(ConfigMap.class.getName()), info.getClassNamesToRegisterForReflection());
    }

    private static SimpleEntry<Index, ClassInfo> getClassInfo(Class<? extends Reconciler<?>> testClass) {
        final var index = TestClassIndexer.readIndex(testClass);
        return new SimpleEntry<>(index, index.getClassByName(DotName.createSimple(testClass.getName())));
    }

    private static SimpleEntry<Index, ResourceAssociatedAugmentedClassInfo> create(Class<? extends Reconciler<?>> testClass) {
        final var indexAndClassInfo = getClassInfo(testClass);
        final var classInfo = indexAndClassInfo.getValue();
        return new SimpleEntry<>(indexAndClassInfo.getKey(),
                new ResourceAssociatedAugmentedClassInfo(classInfo, Constants.RECONCILER, 1,
                        ConfigurationUtils.getReconcilerName(
                                classInfo)));
    }

    private static class ConfigMapReconciler implements Reconciler<ConfigMap> {

        @Override
        public UpdateControl<ConfigMap> reconcile(ConfigMap configMap, Context<ConfigMap> context) {
            return null;
        }
    }

    private static class TestCRReconciler implements Reconciler<TestCR> {

        @Override
        public UpdateControl<TestCR> reconcile(TestCR testCR, Context<TestCR> context) {
            return null;
        }
    }

    private static class TestCR extends CustomResource<TestCRSpec, TestCRStatus> {
    }

    private static class TestCRSpec {
    }

    private static class TestCRStatus {
    }
}
