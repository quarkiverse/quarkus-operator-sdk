package io.quarkiverse.operatorsdk.it;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.AnnotationDependentResourceConfigurator;

@ADRConfigurationAnnotation(AnnotatedDependentResource.VALUE)
public class AnnotatedDependentResource implements DependentResource<TestResource, Service>,
        AnnotationDependentResourceConfigurator<ADRConfigurationAnnotation, ADRConfiguration> {

    public static final int VALUE = 42;
    private ADRConfiguration config;

    @Override
    public ReconcileResult<TestResource> reconcile(Service service, Context<Service> context) {
        return null;
    }

    @Override
    public Class<TestResource> resourceType() {
        return TestResource.class;
    }

    @Override
    public ADRConfiguration configFrom(ADRConfigurationAnnotation adrConfigurationAnnotation,
            ControllerConfiguration<?> controllerConfiguration) {
        return new ADRConfiguration(adrConfigurationAnnotation.value());
    }

    @Override
    public void configureWith(ADRConfiguration adrConfiguration) {
        this.config = adrConfiguration;
    }

    @Override
    public Optional<ADRConfiguration> configuration() {
        return Optional.ofNullable(config);
    }
}
