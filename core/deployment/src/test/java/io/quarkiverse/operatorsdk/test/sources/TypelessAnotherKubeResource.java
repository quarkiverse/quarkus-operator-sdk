package io.quarkiverse.operatorsdk.test.sources;

import static io.quarkiverse.operatorsdk.test.sources.TypelessKubeResource.*;

import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesDependentResource;

public class TypelessAnotherKubeResource extends GenericKubernetesDependentResource<TestCR> implements Deleter<TestCR> {

    private static final GroupVersionKind GVK = new GroupVersionKind(GROUP, VERSION, KIND);

    public TypelessAnotherKubeResource() {
        super(GVK);
    }

}
