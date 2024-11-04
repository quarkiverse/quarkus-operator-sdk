package io.quarkiverse.operatorsdk.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;

public class CRDInfos {
    private final Map<String, Map<String, CRDInfo>> infos;
    private final static String CRD_SPEC_VERSION = HasMetadata.getVersion(CustomResourceDefinition.class);

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
                        .filter(crdInfo -> CRD_SPEC_VERSION.equals(crdInfo.getCrdSpecVersion())))
                .collect(Collectors.toMap(CRDInfo::getCrdName, Function.identity()));
    }

    public Map<String, Map<String, CRDInfo>> getExisting() {
        return infos;
    }

    public void addCRDInfoFor(String crdName, String version, CRDInfo crdInfo) {
        getOrCreateCRDSpecVersionToInfoMapping(crdName).put(version, crdInfo);
    }
}
