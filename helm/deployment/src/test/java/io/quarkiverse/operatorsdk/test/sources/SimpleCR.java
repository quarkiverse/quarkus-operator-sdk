package io.quarkiverse.operatorsdk.test.sources;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("example.com")
@Version(value = SimpleCR.VERSION, storage = false)
@Kind(SimpleCR.KIND)
public class SimpleCR extends CustomResource<SimpleSpec, SimpleStatus> {

    public static final String VERSION = "v1";
    public static final String KIND = "SimpleCR";
}
