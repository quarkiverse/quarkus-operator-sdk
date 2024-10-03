package io.quarkiverse.operatorsdk.test.sources;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("halkyon.io")
@Version(ExternalV1.VERSION)
@Kind(External.KIND)
public class ExternalV1 extends CustomResource<Void, Void> {
    public static final String VERSION = "v1";
}
