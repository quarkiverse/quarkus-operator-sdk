package io.quarkiverse.operatorsdk.runtime;

import java.util.HashMap;
import java.util.Map;

public class ContextStoredCRDInfos {
    private final Map<String, Map<String, CRDInfo>> infos;

    public ContextStoredCRDInfos() {
        this(new HashMap<>());
    }

    public ContextStoredCRDInfos(ContextStoredCRDInfos other) {
        this(new HashMap<>(other.infos));
    }

    private ContextStoredCRDInfos(Map<String, Map<String, CRDInfo>> infos) {
        this.infos = infos;
    }

    public Map<String, CRDInfo> getCRDInfosFor(String crdName) {
        return infos.computeIfAbsent(crdName, k -> new HashMap<>());
    }

    public Map<String, Map<String, CRDInfo>> getExisting() {
        return infos;
    }

    public void putAll(Map<String, Map<String, CRDInfo>> toAdd) {
        infos.putAll(toAdd);
    }

    public void addCRDInfoFor(String crdName, String version, CRDInfo crdInfo) {
        getCRDInfosFor(crdName).put(version, crdInfo);
    }
}
