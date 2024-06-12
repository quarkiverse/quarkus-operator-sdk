package io.quarkiverse.operatorsdk.it;

import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.InformerConfig;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

@KubernetesDependent(informerConfig = @InformerConfig(labelSelector = ReadOnlyDependentResource.LABEL_SELECTOR))
public class ReadOnlyDependentResource extends KubernetesDependentResource<Deployment, ConfigMap> implements
        SecondaryToPrimaryMapper<Deployment> {

    public static final String LABEL_SELECTOR = "readonly";
    public static final String NAME = "read-only";

    public ReadOnlyDependentResource() {
        super(Deployment.class);
    }

    @Override
    public Set<ResourceID> toPrimaryResourceIDs(Deployment deployment) {
        return Collections.emptySet();
    }

    public static class ReadOnlyReadyCondition implements Condition<Deployment, ConfigMap> {

        @Override
        public boolean isMet(DependentResource<Deployment, ConfigMap> dependentResource,
                ConfigMap configMap, Context<ConfigMap> context) {
            return false;
        }
    }

}
