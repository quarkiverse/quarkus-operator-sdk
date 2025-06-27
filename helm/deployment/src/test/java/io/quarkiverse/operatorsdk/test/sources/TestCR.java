package io.quarkiverse.operatorsdk.test.sources;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("josdk.quarkiverse.io")
@Version("v1")
public class TestCR extends CustomResource<Void, Void> {

}
