package io.quarkiverse.operatorsdk.bundle.sources;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

@Group(V1Beta1CRD.GROUP)
@Version(V1Beta1CRD.VERSION)
@Kind(V1Beta1CRD.KIND)
public class V1Beta1CRD extends CustomResource<V1Beta1CRD.Spec, Void> {

    public static final String GROUP = "test.com";
    public static final String VERSION = "v2";
    public static final String KIND = "V1Beta1";
    public static final String CR_NAME = "v1beta1s." + GROUP;

    public record Spec(String value) {
    }
}
