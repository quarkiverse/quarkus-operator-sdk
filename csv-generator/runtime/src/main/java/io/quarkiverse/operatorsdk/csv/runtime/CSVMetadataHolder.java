package io.quarkiverse.operatorsdk.csv.runtime;

public class CSVMetadataHolder {
    public final String name;
    public final String description;
    public final String displayName;
    public final String[] keywords;
    public final String providerName;
    public final String providerURL;
    public final String replaces;
    public final String version;
    public final String maturity;
    public final Maintainer[] maintainers;

    public static class Maintainer {
        public final String name;
        public final String email;

        public Maintainer(String name, String email) {
            this.name = name;
            this.email = email;
        }
    }

    public CSVMetadataHolder(CSVMetadataHolder other) {
        this(other.name, other.description, other.displayName, other.keywords, other.providerName, other.providerURL,
                other.replaces, other.version, other.maturity, other.maintainers);
    }

    public CSVMetadataHolder(String name) {
        this(name, null, null, null, null, null, null, null, null, null);
    }

    public CSVMetadataHolder(String name, String description, String displayName, String[] keywords, String providerName,
            String providerURL, String replaces, String version, String maturity, Maintainer[] maintainers) {
        this.name = name;
        this.description = description;
        this.displayName = displayName;
        this.keywords = keywords;
        this.providerName = providerName;
        this.providerURL = providerURL;
        this.replaces = replaces;
        this.version = version;
        this.maturity = maturity;
        this.maintainers = maintainers;
    }
}
