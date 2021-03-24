package io.quarkiverse.operatorsdk.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusControllerConfiguration<R extends CustomResource> implements ControllerConfiguration<R> {

    private final String associatedControllerClassName;
    private final String name;
    private final String crdName;
    private String finalizer;
    private final boolean generationAware;
    private Set<String> namespaces;
    private RetryConfiguration retryConfiguration;
    private final String crClass;
    private Class<R> clazz;
    private final boolean registrationDelayed;

    @RecordableConstructor
    public QuarkusControllerConfiguration(
            String associatedControllerClassName,
            String name,
            String crdName,
            boolean generationAware,
            String crClass,
            boolean registrationDelayed, Set<String> namespaces, String finalizer) {
        this.associatedControllerClassName = associatedControllerClassName;
        this.name = name;
        this.crdName = crdName;
        this.generationAware = generationAware;
        this.crClass = crClass;
        this.registrationDelayed = registrationDelayed;
        this.retryConfiguration = ControllerConfiguration.super.getRetryConfiguration();
        setNamespaces(namespaces);
        setFinalizer(finalizer);
    }

    public static Set<String> asSet(String[] namespaces) {
        return namespaces == null || namespaces.length == 0
                ? Collections.emptySet()
                : Set.of(namespaces);
    }

    // Needed for Quarkus to find the associated constructor parameter
    public String getCrdName() {
        return getCRDName();
    }

    // Needed for Quarkus to find the associated constructor parameter
    public String getCrClass() {
        return crClass;
    }

    public boolean isRegistrationDelayed() {
        return registrationDelayed;
    }

    @Override
    public Class<R> getCustomResourceClass() {
        if (clazz == null) {
            clazz = (Class<R>) loadClass(crClass);
        }
        return clazz;
    }

    private Class<?> loadClass(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Couldn't find class " + className);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getCRDName() {
        return crdName;
    }

    @Override
    public String getFinalizer() {
        return finalizer;
    }

    public void setFinalizer(String finalizer) {
        this.finalizer = finalizer != null && !finalizer.isBlank() ? finalizer
                : ControllerUtils
                        .getDefaultFinalizerName(crdName);
    }

    @Override
    public boolean isGenerationAware() {
        return generationAware;
    }

    @Override
    public String getAssociatedControllerClassName() {
        return associatedControllerClassName;
    }

    @Override
    public Set<String> getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(Collection<String> namespaces) {
        // todo:       this.namespaces = namespaces != null && !namespaces.isEmpty() ? Set.copyOf(namespaces) : Collections.emptySet();
        this.namespaces = namespaces != null && !namespaces.isEmpty() ? new HashSet<>(namespaces) : Collections.emptySet();
    }

    @Override
    public RetryConfiguration getRetryConfiguration() {
        return retryConfiguration;
    }

    public void setRetryConfiguration(RetryConfiguration retryConfiguration) {
        this.retryConfiguration = retryConfiguration != null ? retryConfiguration
                : ControllerConfiguration.super.getRetryConfiguration();
    }
}
