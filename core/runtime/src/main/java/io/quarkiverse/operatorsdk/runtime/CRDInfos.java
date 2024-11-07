package io.quarkiverse.operatorsdk.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class CRDInfos {
    private final Map<String, Map<String, CRDInfo>> infos;

    public CRDInfos() {
        this(new ConcurrentHashMap<>());
    }

    public CRDInfos(CRDInfos other) {
        this(new ConcurrentHashMap<>(other.infos));
    }

    @RecordableConstructor // constructor needs to be recordable for the class to be passed around by Quarkus
    private CRDInfos(Map<String, Map<String, CRDInfo>> infos) {
        this.infos = infos;
    }

    @IgnoreProperty
    public Map<String, CRDInfo> getOrCreateCRDSpecVersionToInfoMapping(String crdName) {
        return infos.computeIfAbsent(crdName, k -> new HashMap<>());
    }

    @IgnoreProperty
    public Map<String, CRDInfo> getCRDNameToInfoMappings() {
        return infos
                .values().stream()
                // only keep CRD v1 infos
                .flatMap(entry -> entry.values().stream()
                        .filter(crdInfo -> CRDUtils.DEFAULT_CRD_SPEC_VERSION.equals(crdInfo.getCrdSpecVersion())))
                .collect(Collectors.toMap(CRDInfo::getCrdName, Function.identity()));
    }

    public void addCRDInfo(CRDInfo crdInfo) {
        getOrCreateCRDSpecVersionToInfoMapping(crdInfo.getCrdName()).put(crdInfo.getCrdSpecVersion(), crdInfo);
    }

    // Needed by Quarkus: if this method isn't present, state is not properly set
    @SuppressWarnings("unused")
    public Map<String, Map<String, CRDInfo>> getInfos() {
        return infos;
    }
}
