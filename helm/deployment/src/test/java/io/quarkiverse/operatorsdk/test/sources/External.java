package io.quarkiverse.operatorsdk.test.sources;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("halkyon.io")
@Version(value = External.VERSION, storage = false)
@Kind(External.KIND)
public class External extends CustomResource<Void, Void> {
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "External";
}
