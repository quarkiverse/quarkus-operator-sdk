package io.quarkiverse.operatorsdk.bundle.sources;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("samples.javaoperatorsdk.io")
@Version("v1alpha1")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class First extends CustomResource<Void, Void> implements Namespaced {

    public First() {
    }
}
