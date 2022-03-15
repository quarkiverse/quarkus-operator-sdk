package io.quarkiverse.operatorsdk.deployment;

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.operatorsdk.runtime.CRDInfo;

public class ContextStoredCRDInfos {
    private final Map<String, Map<String, CRDInfo>> infos = new HashMap<>();

    Map<String, CRDInfo> getCRDInfosFor(String crdName) {
        return infos.computeIfAbsent(crdName, k -> new HashMap<>());
    }

    public Map<String, Map<String, CRDInfo>> getExisting() {
        return infos;
    }

    void putAll(Map<String, Map<String, CRDInfo>> toAdd) {
        infos.putAll(toAdd);
    }
}
