package io.quarkiverse.operatorsdk.runtime;

public class CSVMetadataHolder {
    public final String name;
    public final String description;
    public final String displayName;
    public final String[] keywords;

    public CSVMetadataHolder(String name, String description, String displayName, String[] keywords) {
        this.name = name;
        this.description = description;
        this.displayName = displayName;
        this.keywords = keywords;
    }
}
