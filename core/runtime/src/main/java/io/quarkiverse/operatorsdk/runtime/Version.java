package io.quarkiverse.operatorsdk.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.Properties;

import org.jboss.logging.Logger;

import io.javaoperatorsdk.operator.api.config.Utils;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class Version extends io.javaoperatorsdk.operator.api.config.Version {
    private static final Logger log = Logger.getLogger(Version.class.getName());
    public static final String UNKNOWN = "unknown";

    private final String extensionVersion;
    private final String extensionBranch;
    private final String extensionCommit;
    private final Date extensionBuildTime;

    @RecordableConstructor // constructor needs to be recordable for the class to be passed around by Quarkus
    public Version(String commit, Date builtTime, String extensionVersion, String extensionCommit,
            String extensionBranch, Date extensionBuildTime) {
        super(commit, builtTime);
        this.extensionVersion = extensionVersion;
        this.extensionBranch = extensionBranch;
        this.extensionCommit = extensionCommit;
        this.extensionBuildTime = extensionBuildTime;
    }

    public String getExtensionVersion() {
        return extensionVersion;
    }

    @SuppressWarnings("unused")
    public String getExtensionBranch() {
        return extensionBranch;
    }

    public String getExtensionCommit() {
        return extensionCommit;
    }

    @SuppressWarnings("unused")
    public Date getExtensionBuildTime() {
        return extensionBuildTime;
    }

    @IgnoreProperty
    public String getExtensionCompleteVersion() {
        final var branch = Version.UNKNOWN.equals(extensionBranch)
                ? " on branch: " + extensionBranch
                : "";
        return extensionVersion + " (commit: " + extensionCommit + branch + ") built on " + extensionBuildTime;
    }

    public String getQuarkusVersion() {
        return Versions.QUARKUS;
    }

    public static Version loadFromProperties() {
        final var sdkVersion = Utils.VERSION;
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("extension-version.properties");
        Properties properties = new Properties();
        if (is != null) {
            try {
                properties.load(is);
            } catch (IOException e) {
                log.warnf("Couldn't load extension version information: {0}", e.getMessage());
            }
        } else {
            log.warn("Couldn't find extension-version.properties file. Default version information will be used.");
        }

        Date builtTime;
        try {
            String time = properties.getProperty("git.build.time");
            if (time != null) {
                builtTime = Date.from(Instant.parse(time));
            } else {
                builtTime = Date.from(Instant.EPOCH);
            }
        } catch (Exception e) {
            log.debug("Couldn't parse git.build.time property", e);
            builtTime = Date.from(Instant.EPOCH);
        }

        return new Version(sdkVersion.getCommit(), sdkVersion.getBuiltTime(),
                properties.getProperty("git.build.version", UNKNOWN),
                properties.getProperty("git.commit.id.abbrev", UNKNOWN),
                properties.getProperty("git.branch", UNKNOWN),
                builtTime);
    }
}
