package io.quarkiverse.operatorsdk.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CRDInfos {
    private final Map<String, Map<String, CRDInfo>> infos;

    public CRDInfos() {
        this(new HashMap<>());
    }

    public CRDInfos(CRDInfos other) {
        this(new HashMap<>(other.infos));
    }

    private CRDInfos(Map<String, Map<String, CRDInfo>> infos) {
        this.infos = infos;
    }

    public Map<String, CRDInfo> getOrCreateCRDSpecVersionToInfoMapping(String crdName) {
        return infos.computeIfAbsent(crdName, k -> new HashMap<>());
    }

    public Map<String, CRDInfo> getCRDNameToInfoMappings() {
        return infos
                .values().stream()
                // only keep CRD v1 infos
                .flatMap(entry -> entry.values().stream()
                        .filter(crdInfo -> CRDUtils.DEFAULT_CRD_SPEC_VERSION.equals(crdInfo.getCrdSpecVersion())))
                .collect(Collectors.toMap(CRDInfo::getCrdName, Function.identity()));
    }

    public Map<String, Map<String, CRDInfo>> getExisting() {
        return infos;
    }

    public void addCRDInfoFor(String crdName, String crdSpecVersion, CRDInfo crdInfo) {
        getOrCreateCRDSpecVersionToInfoMapping(crdName).put(crdSpecVersion, crdInfo);
    }
}
