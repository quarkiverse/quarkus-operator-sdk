package io.quarkiverse.operatorsdk.bundle.sources;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata;

@Group("samples.javaoperatorsdk.io")
@Version("v1alpha1")
@CSVMetadata(displayName = Third.DISPLAY, description = Third.DESCRIPTION)
public class Third extends CustomResource<Void, Void> implements Namespaced {

  public static final String DESCRIPTION = "Third description";
  public static final String DISPLAY = "Third display";
}
