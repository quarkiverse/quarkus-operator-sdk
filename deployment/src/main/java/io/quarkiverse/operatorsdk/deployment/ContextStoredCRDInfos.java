package io.quarkiverse.operatorsdk.deployment;

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.operatorsdk.runtime.CRDInfo;

public class ContextStoredCRDInfos {
    private Map<String, Map<String, CRDInfo>> infos = new HashMap<>();

    public Map<String, CRDInfo> getCRDInfosFor(String crdName) {
        return infos.computeIfAbsent(crdName, k -> new HashMap<>());
    }

    void putAll(Map<String, Map<String, CRDInfo>> toAdd) {
        infos.putAll(toAdd);
    }
}
