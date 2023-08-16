package io.quarkiverse.operatorsdk.test.sources;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("halkyon.io")
@Version("v1alpha1")
public class External extends CustomResource<Void, Void> {

    public static final String DISPLAY_NAME = "External display name";
    public static final String DESCRIPTION = "External description";
}
