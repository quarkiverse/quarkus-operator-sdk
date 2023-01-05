package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("org.keycloak")
@Version("v1alpha2")
public class Keycloak extends CustomResource<Void, Void> {

}
