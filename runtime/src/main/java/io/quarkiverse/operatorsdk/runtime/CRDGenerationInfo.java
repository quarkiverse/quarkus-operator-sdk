package io.quarkiverse.operatorsdk.runtime;

import java.io.File;
import java.util.*;
import java.util.function.BiFunction;

public class CRDGenerationInfo {
    private boolean apply;
    private boolean validate;
    private Map<String, Map<String, CRDInfo>> crds;

    public CRDGenerationInfo() {
        this(false, true, Collections.emptyMap());
    }

    private CRDGenerationInfo(boolean apply, boolean validate, Map<String, Map<String, String>> crds) {
        this.apply = apply;
        this.validate = validate;
        final var converted = new HashMap<String, Map<String, CRDInfo>>();
        crds.forEach((crdName, initialVersionToCRDInfoMap) -> {
            final var versionToCRDInfo = converted.computeIfAbsent(crdName, s -> new HashMap<>());
            initialVersionToCRDInfoMap
                    .forEach((version, path) -> versionToCRDInfo.put(version, new CRDInfo(crdName, path)));
        });
        this.crds = Collections.unmodifiableMap(converted);
    }

    public boolean isApply() {
        return apply;
    }

    public void setApply(boolean apply) {
        this.apply = apply;
    }

    public void setValidate(boolean validate) {
        this.validate = validate;
    }

    public Map<String, Map<String, CRDInfo>> getCrds() {
        return crds;
    }

    public void setCrds(Map<String, Map<String, CRDInfo>> crds) {
        this.crds = crds;
    }

    public static CRDGenerationInfoBuilder builder() {
        return new CRDGenerationInfoBuilder();
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

    public static class CRDGenerationInfoBuilder {
        private List<String> versions;
        private File generatedCRDDirectory;
        private boolean validate;
        private boolean apply;
        private BiFunction<String, String, String> namingStrategy;
        private List<String> crdNames = new ArrayList<>(7);

        public CRDGenerationInfoBuilder forCRDVersions(List<String> versions) {
            this.versions = versions;
            return this;
        }

        public CRDGenerationInfoBuilder withTargetDir(File generatedCRDDirectory) {
            this.generatedCRDDirectory = generatedCRDDirectory;
            return this;
        }

        public CRDGenerationInfo build() {
            // for each CRD name, get the CRD file path for each requested CRD spec version
            final Map<String, Map<String, String>> crdNamesToVersionsMap = new HashMap<>(crdNames.size());
            crdNames.forEach(name -> versions.forEach(v -> crdNamesToVersionsMap
                    .computeIfAbsent(name, s -> new HashMap<>())
                    .put(v, new File(generatedCRDDirectory, namingStrategy.apply(name, v) + ".yml").getAbsolutePath())));

            return new CRDGenerationInfo(apply, validate, crdNamesToVersionsMap);
        }

        public CRDGenerationInfoBuilder validatingCRDs(boolean validateCustomResources) {
            this.validate = validateCustomResources;
            return this;
        }

        public CRDGenerationInfoBuilder applyingCRDs(boolean apply) {
            this.apply = apply;
            return this;
        }

        public CRDGenerationInfoBuilder withNamingStrategy(BiFunction<String, String, String> namingStrategy) {
            this.namingStrategy = namingStrategy;
            return this;
        }

        public CRDGenerationInfoBuilder withCustomResource(String crdName) {
            crdNames.add(crdName);
            return this;
        }
    }

}
