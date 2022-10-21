package io.quarkiverse.operatorsdk.bundle.runtime;

import java.util.Objects;

public class CSVMetadataHolder {
    public final String name;
    public final String description;
    public final String displayName;
    public final Annotations annotations;
    public final String[] keywords;
    public final String providerName;
    public final String providerURL;
    public final String replaces;
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

        public Annotations(String containerImage, String repository, String capabilities, String categories, boolean certified,
                String almExamples) {
            this.containerImage = containerImage;
            this.repository = repository;
            this.capabilities = capabilities;
            this.categories = categories;
            this.certified = certified;
            this.almExamples = almExamples;
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

    public CSVMetadataHolder(CSVMetadataHolder other) {
        this(other.name, other.description, other.displayName, other.annotations, other.keywords, other.providerName,
                other.providerURL,
                other.replaces, other.version, other.maturity, other.minKubeVersion, other.maintainers, other.links, other.icon,
                other.installModes, other.permissionRules, other.requiredCRDs);
    }

    public CSVMetadataHolder(String name) {
        this(name, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public CSVMetadataHolder(String name, String description, String displayName, Annotations annotations, String[] keywords,
            String providerName,
            String providerURL, String replaces, String version, String maturity, String minKubeVersion,
            Maintainer[] maintainers, Link[] links, Icon[] icon,
            InstallMode[] installModes, PermissionRule[] permissionRules, RequiredCRD[] requiredCRDs) {
        this.name = name;
        this.description = description;
        this.displayName = displayName;
        this.annotations = annotations;
        this.keywords = keywords;
        this.providerName = providerName;
        this.providerURL = providerURL;
        this.replaces = replaces;
        this.version = version;
        this.maturity = maturity;
        this.minKubeVersion = minKubeVersion;
        this.maintainers = maintainers;
        this.links = links;
        this.icon = icon;
        this.installModes = installModes;
        this.permissionRules = permissionRules;
        this.requiredCRDs = requiredCRDs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CSVMetadataHolder that = (CSVMetadataHolder) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
