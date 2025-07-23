package io.quarkiverse.operatorsdk.test.sources;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("example.com")
@Version(SimpleCRV2.VERSION)
@Kind(SimpleCR.KIND)
public class SimpleCRV2 extends CustomResource<SimpleSpecV2, SimpleStatus> {
    public static final String VERSION = "v2";
}
