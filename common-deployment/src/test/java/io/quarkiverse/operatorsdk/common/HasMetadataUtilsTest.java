package io.quarkiverse.operatorsdk.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;
import io.quarkus.test.common.TestClassIndexer;

class HasMetadataUtilsTest {

    @Test
    void getFullResourceName() {
        final ClassInfo info = getClassInfo(TestHasMetadata.class);
        assertEquals(HasMetadata.getFullResourceName(TestHasMetadata.class), HasMetadataUtils.getFullResourceName(info));
        assertEquals(HasMetadata.getFullResourceName(SingularHM.class), HasMetadataUtils.getFullResourceName(getClassInfo(
                SingularHM.class)));
        assertEquals(HasMetadata.getFullResourceName(PluralHM.class), HasMetadataUtils.getFullResourceName(getClassInfo(
                PluralHM.class)));
    }

    private static ClassInfo getClassInfo(Class<? extends HasMetadata> testClass) {
        return TestClassIndexer.readIndex(testClass)
                .getClassByName(DotName.createSimple(testClass.getName()));
    }

    @Test
    void getPlural() {
        assertEquals(HasMetadata.getPlural(TestHasMetadata.class), HasMetadataUtils.getPlural(getClassInfo(
                TestHasMetadata.class)));
        assertEquals(HasMetadata.getPlural(PluralHM.class), HasMetadataUtils.getPlural(getClassInfo(
                PluralHM.class)));
    }

    @Test
    void getGroup() {
        assertEquals(HasMetadata.getGroup(TestHasMetadata.class),
                HasMetadataUtils.getGroup(getClassInfo(TestHasMetadata.class)));
    }

    @Test
    void getSingular() {
        assertEquals(HasMetadata.getSingular(TestHasMetadata.class),
                HasMetadataUtils.getSingular(getClassInfo(TestHasMetadata.class)));
        assertEquals(HasMetadata.getSingular(SingularHM.class),
                HasMetadataUtils.getSingular(getClassInfo(SingularHM.class)));
    }

    @Test
    void getKind() {
        assertEquals(HasMetadata.getKind(TestHasMetadata.class), HasMetadataUtils.getKind(getClassInfo(
                TestHasMetadata.class)));
    }

    @Test
    void getVersion() {
        assertEquals(HasMetadata.getVersion(TestHasMetadata.class),
                HasMetadataUtils.getVersion(getClassInfo(TestHasMetadata.class)));
    }

    static class AbstractHasMetadata implements HasMetadata {

        @Override
        public ObjectMeta getMetadata() {
            return null;
        }

        @Override
        public void setMetadata(ObjectMeta metadata) {

        }

        @Override
        public void setApiVersion(String version) {

        }
    }

    @Group("foo")
    @Kind("test")
    @Version("v1")
    static class TestHasMetadata extends AbstractHasMetadata {
    }

    @Singular("sing")
    @Group("halkyon.io")
    static class SingularHM extends AbstractHasMetadata {
    }

    @Plural("plurals")
    @Group("halkyon.io")
    static class PluralHM extends AbstractHasMetadata {
    }
}
