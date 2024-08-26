package io.quarkiverse.operatorsdk.test.sources;

import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesDependentResource;

public class TypelessKubeResource extends GenericKubernetesDependentResource<TestCR> {

    public static final String GROUP = "crd.josdk.quarkiverse.io";
    public static final String KIND = "typeless";
    public static final String VERSION = "v1";
    private static final GroupVersionKind GVK = new GroupVersionKind(GROUP, VERSION, KIND);

    public TypelessKubeResource() {
        super(GVK);
    }

}
