package io.halkyon;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1alpha1")
@Group("halkyon.io")
public class ExposedApp extends CustomResource<ExposedAppSpec, ExposedAppStatus> implements Namespaced {

    @Override
    protected ExposedAppSpec initSpec() {
        return new ExposedAppSpec();
    }

    @Override
    protected ExposedAppStatus initStatus() {
        return new ExposedAppStatus();
    }
}
