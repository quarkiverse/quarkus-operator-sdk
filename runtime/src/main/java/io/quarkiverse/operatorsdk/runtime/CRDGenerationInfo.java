package io.quarkiverse.operatorsdk.runtime;

import java.util.Collections;
import java.util.Map;

public class CRDGenerationInfo {
    private boolean apply;
    private boolean validate;
    private Map<String, Map<String, CRDInfo>> crds;

    // Needed by Quarkus: class needs to be serializable
    public CRDGenerationInfo() {
        this(false, true, Collections.emptyMap());
    }

    public CRDGenerationInfo(boolean apply, boolean validate, Map<String, Map<String, CRDInfo>> crdInfos) {
        this.apply = apply;
        this.validate = validate;
        this.crds = Collections.unmodifiableMap(crdInfos);
    }

    // Needed by Quarkus: class needs to be serializable
    public void setApply(boolean apply) {
        this.apply = apply;
    }

    // Needed by Quarkus: class needs to be serializable
    public void setValidate(boolean validate) {
        this.validate = validate;
    }

    // Needed by Quarkus: class needs to be serializable
    public void setCrds(Map<String, Map<String, CRDInfo>> crds) {
        this.crds = crds;
    }

    // Needed by Quarkus: if this method isn't present, state is not properly set
    public boolean isApply() {
        return apply;
    }

    // Needed by Quarkus: if this method isn't present, state is not properly set
    public boolean isValidate() {
        return validate;
    }

    // Needed by Quarkus: if this method isn't present, state is not properly set
    public Map<String, Map<String, CRDInfo>> getCrds() {
        return crds;
    }

    public boolean isApplyCRDs() {
        return apply;
    }

    public Map<String, CRDInfo> getCRDFiles(String crdName) {
        return crds.get(crdName);
    }

    public boolean isValidateCRDs() {
        return validate;
    }
}
