package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.quarkiverse.operatorsdk.it.EmptyCR.EmptySpec;

@Group("josdk.quarkiverse.io")
@Version("v1alpha1")
public class EmptyCR extends CustomResource<EmptySpec, Void> {

    static class EmptySpec {

    }
}
