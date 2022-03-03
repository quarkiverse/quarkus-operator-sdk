package io.quarkiverse.operatorsdk.bundle.deployment;

import java.util.Objects;

import io.quarkiverse.operatorsdk.runtime.ResourceInfo;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class AugmentedResourceInfo extends ResourceInfo {
    private final String csvGroupName;

    @RecordableConstructor
    public AugmentedResourceInfo(ResourceInfo cri, String csvGroupName) {
        super(cri.getGroup(), cri.getVersion(), cri.getKind(), cri.getSingular(), cri.getPlural(), cri.getShortNames(),
                cri.isStorage(), cri.isServed(), cri.getScope(), cri.getResourceClassName(), cri.getSpecClassName(),
                cri.getStatusClassName(), cri.getResourceFullName(), cri.getControllerName());
        this.csvGroupName = csvGroupName;
    }

    public String getCsvGroupName() {
        return csvGroupName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        AugmentedResourceInfo that = (AugmentedResourceInfo) o;

        return Objects.equals(csvGroupName, that.csvGroupName);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (csvGroupName != null ? csvGroupName.hashCode() : 0);
        return result;
    }
}
