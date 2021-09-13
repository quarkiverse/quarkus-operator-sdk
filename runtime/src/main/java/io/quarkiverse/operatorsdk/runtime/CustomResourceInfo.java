package io.quarkiverse.operatorsdk.runtime;

import java.util.Optional;

import io.fabric8.kubernetes.model.Scope;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class CustomResourceInfo {
    private final String group;
    private final String version;
    private final String kind;
    private final String singular;
    private final String plural;
    private final String[] shortNames;
    private final boolean storage;
    private final boolean served;
    private final Scope scope;
    private final String crClassName;
    private final Optional<String> specClassName;
    private final Optional<String> statusClassName;
    private final String controllerName;

    @RecordableConstructor
    public CustomResourceInfo(String group, String version, String kind, String singular, String plural, String[] shortNames,
            boolean storage, boolean served, Scope scope, String crClassName, Optional<String> specClassName,
            Optional<String> statusClassName, String controllerName) {
        this.group = group;
        this.version = version;
        this.kind = kind;
        this.singular = singular;
        this.plural = plural;
        this.shortNames = shortNames;
        this.storage = storage;
        this.served = served;
        this.scope = scope;
        this.crClassName = crClassName;
        this.specClassName = specClassName;
        this.statusClassName = statusClassName;
        this.controllerName = controllerName;
    }

    public String getGroup() {
        return group;
    }

    public String getVersion() {
        return version;
    }

    public String getKind() {
        return kind;
    }

    public String getSingular() {
        return singular;
    }

    public String getPlural() {
        return plural;
    }

    public String[] getShortNames() {
        return shortNames;
    }

    public boolean isStorage() {
        return storage;
    }

    public boolean isServed() {
        return served;
    }

    public Scope getScope() {
        return scope;
    }

    public String getCrClassName() {
        return crClassName;
    }

    public Optional<String> getSpecClassName() {
        return specClassName;
    }

    public Optional<String> getStatusClassName() {
        return statusClassName;
    }

    public String getControllerName() {
        return controllerName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        CustomResourceInfo that = (CustomResourceInfo) o;

        if (!group.equals(that.group))
            return false;
        if (!version.equals(that.version))
            return false;
        return kind.equals(that.kind);
    }

    @Override
    public int hashCode() {
        int result = group.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + kind.hashCode();
        return result;
    }
}
