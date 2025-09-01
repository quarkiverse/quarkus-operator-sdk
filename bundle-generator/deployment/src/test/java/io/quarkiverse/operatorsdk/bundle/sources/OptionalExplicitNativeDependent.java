package io.quarkiverse.operatorsdk.bundle.sources;

import io.fabric8.openshift.api.model.monitoring.v1.ServiceMonitor;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;

@CSVMetadata.Optional
public class OptionalExplicitNativeDependent extends KubernetesDependentResource<ServiceMonitor, Third> {
}
