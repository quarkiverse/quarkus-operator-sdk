package io.quarkiverse.operatorsdk.bundle.sources;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

@Group(SecondExternal.GROUP)
@Version(SecondExternal.VERSION)
@Kind(SecondExternal.KIND)
public class SecondExternal extends CustomResource<Void, Void> {

    public static final String GROUP = "halkyon.io";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "ExternalAgain";
}
