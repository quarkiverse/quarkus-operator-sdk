package io.quarkiverse.operatorsdk.it.cdi;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("josdk.quarkiverse.io")
@Version("v1alpha1")
@ShortNames("tr")
public class TestResource extends CustomResource<TestSpec, TestStatus> {
}
