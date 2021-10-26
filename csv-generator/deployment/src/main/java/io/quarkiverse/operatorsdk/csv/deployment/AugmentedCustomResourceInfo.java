package io.quarkiverse.operatorsdk.csv.deployment;

import io.quarkiverse.operatorsdk.common.CustomResourceInfo;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class AugmentedCustomResourceInfo extends CustomResourceInfo {
    private final String csvGroupName;

    @RecordableConstructor
    public AugmentedCustomResourceInfo(CustomResourceInfo cri, String csvGroupName) {
        super(cri.getGroup(), cri.getVersion(), cri.getKind(), cri.getSingular(), cri.getPlural(), cri.getShortNames(),
                cri.isStorage(), cri.isServed(), cri.getScope(), cri.getCrClassName(), cri.getSpecClassName(),
                cri.getStatusClassName(), cri.getCrdName(), cri.getControllerName());
        this.csvGroupName = csvGroupName;
    }

    public String getCsvGroupName() {
        return csvGroupName;
    }
}
