package io.quarkiverse.operatorsdk.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.operator.api.config.informer.FieldSelector;

class QuarkusFieldSelectorTest {

    @Test
    void shouldCreateFieldSelector() {
        assertEquals(new FieldSelector.Field("spec.nodeName", "someName", true), QuarkusFieldSelector
                .from("spec.nodeName != someName", QuarkusFieldSelector.knownValidFields.get(Pod.class.getName()), Pod.class));
        assertEquals(new FieldSelector.Field("metadata.name", "goo"),
                QuarkusFieldSelector.from("metadata.name=goo", null, HasMetadata.class));
    }

    @Test
    void invalidSelectorShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> QuarkusFieldSelector.from("foo", null, Pod.class));
        assertThrows(IllegalArgumentException.class, () -> QuarkusFieldSelector.from("foo=", null, Pod.class));
        assertThrows(IllegalArgumentException.class, () -> QuarkusFieldSelector.from("foo=!", null, Pod.class));
        assertThrows(IllegalArgumentException.class, () -> QuarkusFieldSelector.from("foo==", null, Pod.class));
        assertThrows(IllegalArgumentException.class, () -> QuarkusFieldSelector.from("foo!=", null, Pod.class));
        assertThrows(IllegalArgumentException.class, () -> QuarkusFieldSelector.from("foo != ", null, Pod.class));
        assertThrows(IllegalArgumentException.class, () -> QuarkusFieldSelector.from("foo = ", null, Pod.class));
        assertThrows(IllegalArgumentException.class, () -> QuarkusFieldSelector.from("foo ==   ", null, Pod.class));
        assertThrows(IllegalArgumentException.class, () -> QuarkusFieldSelector.from("foo == value ", null, Pod.class));
    }

    @Test
    void getValidFieldNamesFor() {
        final var configuration = mock(BuildTimeConfigurationService.class);
        final var crds = new CRDInfos();
        crds.addCRDInfo(new CRDInfo(CRDUtils.crdNameFor(External.class), CRDUtils.DEFAULT_CRD_SPEC_VERSION,
                "src/test/resources/external.crd.yml", Set.of()));
        when(configuration.getCrdInfo()).thenReturn(new CRDGenerationInfo(false, false, crds, Set.of()));

        assertEquals(Set.of("spec"), QuarkusFieldSelector.getValidFieldNamesOrNullIfDefault(External.class, configuration));
    }

    @Test
    void chainedSelectorsShouldWork() {
        assertEquals(
                List.of(new FieldSelector.Field("metadata.name", "value"),
                        new FieldSelector.Field("status.phase", "ready", true)),
                QuarkusFieldSelector.from(List.of("metadata.name=value", "status.phase != ready"), Pod.class, null)
                        .getFields());
    }

    @Group("halkyon.io")
    @Version("v1")
    private static class External extends CustomResource<Void, Void> {
    }
}
