package io.quarkiverse.operatorsdk.bundle.runtime;

import static io.quarkiverse.operatorsdk.bundle.runtime.BundleConfiguration.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CSVMetadataHolder {
    public final String bundleName;
    public final String csvName;
    private final String origin;
    public final String description;
    public final String displayName;
    public final Annotations annotations;
    public final Map<String, String> labels;
    public final String[] keywords;
    public final String providerName;
    public final String providerURL;
    public final String replaces;
    public final String[] skips;
    public final String version;
    public final String maturity;
    public final String minKubeVersion;
    public final Maintainer[] maintainers;
    public final Link[] links;
    public final Icon[] icon;
    public final InstallMode[] installModes;
    public final PermissionRule[] permissionRules;
    public final RequiredCRD[] requiredCRDs;

    public static class Icon {
        public final String fileName;
        public final String mediatype;

        public Icon(String fileName, String mediatype) {
            this.fileName = fileName;
            this.mediatype = mediatype;
        }
    }

    public static class Annotations {
        public final String containerImage;
        public final String repository;
        public final String capabilities;
        public final String categories;
        public final boolean certified;
        public final String almExamples;
        public final String skipRange;
        public final Map<String, String> others;
        private final static Annotations EMPTY = new Annotations(null, null, null, null, false, null, null, Map.of());

        public Annotations(String containerImage, String repository, String capabilities, String categories, boolean certified,
                String almExamples, String skipRange, Map<String, String> others) {
            this.containerImage = containerImage;
            this.repository = repository;
            this.capabilities = capabilities;
            this.categories = categories;
            this.certified = certified;
            this.almExamples = almExamples;
            this.skipRange = skipRange;
            this.others = Collections.unmodifiableMap(others);
        }

        public static Annotations override(Annotations initial, Map<String, String> overrides) {
            if (initial == null) {
                initial = EMPTY;
            }
            final var copy = new HashMap<>(overrides);

            return new Annotations(
                    getOrDefault(copy, CONTAINER_IMAGE_ANNOTATION, initial.containerImage),
                    getOrDefault(copy, REPOSITORY_ANNOTATION, initial.repository),
                    getOrDefault(copy, CAPABILITIES_ANNOTATION, initial.capabilities),
                    getOrDefault(copy, CATEGORIES_ANNOTATION, initial.categories),
                    Boolean.parseBoolean(getOrDefault(copy, CERTIFIED_ANNOTATION, "false")),
                    getOrDefault(copy, ALM_EXAMPLES_ANNOTATION, initial.almExamples),
                    getOrDefault(copy, OLM_SKIP_RANGE_ANNOTATION, initial.skipRange),
                    additionalAnnotationOverrides(initial.others, copy));
        }

        private static Map<String, String> additionalAnnotationOverrides(Map<String, String> others,
                HashMap<String, String> overrides) {
            final var initial = new HashMap<>(others);
            initial.putAll(overrides);
            return initial;
        }

        private static String getOrDefault(Map<String, String> overrides, String annotation, String initialValue) {
            final var removed = overrides.remove(annotation);
            return removed != null ? removed : initialValue;
        }
    }

    public static class Maintainer {
        public final String name;
        public final String email;

        public Maintainer(String name, String email) {
            this.name = name;
            this.email = email;
        }
    }

    public static class Link {
        public final String name;
        public final String url;

        public Link(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    public static class InstallMode {
        public final String type;
        public final boolean supported;

        public InstallMode(String type, boolean supported) {
            this.type = type;
            this.supported = supported;
        }
    }

    public static class PermissionRule {
        public final String[] apiGroups;
        public final String[] resources;
        public final String[] verbs;
        public final String serviceAccountName;

        public PermissionRule(String[] apiGroups, String[] resources, String[] verbs, String serviceAccountName) {
            this.apiGroups = apiGroups;
            this.resources = resources;
            this.verbs = verbs;
            this.serviceAccountName = serviceAccountName;
        }
    }

    public static class RequiredCRD {
        public final String kind;

        public final String name;

        public final String version;

        public RequiredCRD(String kind, String name, String version) {
            this.kind = kind;
            this.name = name;
            this.version = version;
        }

    }

    public CSVMetadataHolder(String bundleName, String version, String replaces, String providerName, String origin) {
        this(bundleName, null, null, null, null, null, null, providerName, null, replaces, null, version, null, null, null,
                null, null,
                null, null,
                null,
                origin);
    }

    public CSVMetadataHolder(String bundleName, String csvName, String description, String displayName, Annotations annotations,
            Map<String, String> labels,
            String[] keywords,
            String providerName,
            String providerURL, String replaces, String[] skips, String version, String maturity,
            String minKubeVersion,
            Maintainer[] maintainers, Link[] links, Icon[] icon,
            InstallMode[] installModes, PermissionRule[] permissionRules, RequiredCRD[] requiredCRDs, String origin) {
        this.bundleName = bundleName;
        assert version != null;
        this.version = version;
        this.csvName = csvName == null ? bundleName + ".v" + version.toLowerCase() : csvName;
        this.description = description;
        this.displayName = displayName;
        this.annotations = annotations;
        this.labels = labels;
        this.keywords = keywords;
        this.providerURL = providerURL;
        this.replaces = replaces;
        this.skips = skips;
        this.maturity = maturity;
        this.minKubeVersion = minKubeVersion;
        this.maintainers = maintainers;
        this.links = links;
        this.icon = icon;
        this.installModes = installModes;
        this.permissionRules = permissionRules;
        this.requiredCRDs = requiredCRDs;
        this.origin = origin;
        this.providerName = providerName;
    }

    /**
     * CSVMetadataHolders are stored as key in Maps and identified by their associated bundle name, so that's what we use for
     * equals and {@link #hashCode()}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CSVMetadataHolder that = (CSVMetadataHolder) o;
        return Objects.equals(bundleName, that.bundleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bundleName);
    }

    public String getOrigin() {
        return origin;
    }
}
