package io.quarkiverse.operatorsdk.test.reconcilers;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("example.com")
@Version("v1")
public abstract class TestResource extends CustomResource<String, Void> {

}
