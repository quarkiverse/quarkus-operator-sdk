package io.quarkiverse.operatorsdk.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.certificates.v1.CertificateSigningRequest;
import io.quarkus.test.common.TestClassIndexer;

public class ClassUtilsTest {

    @Test
    void canFindStatusDirectly() {
        final var index = TestClassIndexer.readIndex(CertificateSigningRequest.class);
        final var info = index.getClassByName(CertificateSigningRequest.class);
        assertTrue(ClassUtils.hasField(index, info, "status"));
    }

    @Test
    void canFindStatusInHierachy() {
        final var index = TestClassIndexer.readIndex(Child.class);
        final var info = index.getClassByName(Child.class);
        assertTrue(ClassUtils.hasField(index, info, "status"));
    }

    @Test
    void shouldNotFindNonExistingField() {
        final var index = TestClassIndexer.readIndex(Child.class);
        final var info = index.getClassByName(Child.class);
        assertFalse(ClassUtils.hasField(index, info, "foo"));
    }

    static abstract class Parent implements HasMetadata {
        private Object status;
    }

    static class Child extends Parent {

        @Override
        public ObjectMeta getMetadata() {
            return null;
        }

        @Override
        public void setMetadata(ObjectMeta objectMeta) {

        }

        @Override
        public void setApiVersion(String s) {

        }
    }
}
