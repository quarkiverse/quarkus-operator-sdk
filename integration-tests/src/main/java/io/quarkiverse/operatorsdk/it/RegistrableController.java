package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;

public interface RegistrableController<R extends CustomResource> extends ResourceController<R> {

    boolean isInitialized();
}
