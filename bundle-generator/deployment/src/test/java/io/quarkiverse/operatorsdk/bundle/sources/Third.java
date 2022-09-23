package io.quarkiverse.operatorsdk.bundle.sources;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("samples.javaoperatorsdk.io")
@Version("v1alpha1")
public class Third extends CustomResource<Void, Void> implements Namespaced {

}
