package io.quarkiverse.operatorsdk.runtime;

import java.util.Date;

import io.javaoperatorsdk.operator.api.config.Utils;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class Version extends io.javaoperatorsdk.operator.api.config.Version {
    public static final String UNKNOWN = "unknown";

    private final String extensionVersion;
    private final String extensionBranch;
    private final String extensionCommit;
    private final String runtimeFabric8Version;
    private final Date extensionBuildTime;

    @RecordableConstructor // constructor needs to be recordable for the class to be passed around by Quarkus
    public Version(String commit, Date builtTime, String extensionVersion, String extensionCommit,
            String extensionBranch, String runtimeFabric8Version, Date extensionBuildTime) {
        super(commit, builtTime);
        this.extensionVersion = extensionVersion;
        this.extensionBranch = extensionBranch;
        this.extensionCommit = extensionCommit;
        this.runtimeFabric8Version = runtimeFabric8Version;
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

    @IgnoreProperty
    public String getSdkCompleteVersion() {
        return getSdkVersion() + " (commit: " + getCommit() + ") built on " + getBuiltTime();
    }

    public String getQuarkusVersion() {
        return Versions.QUARKUS;
    }

    public String getRuntimeFabric8Version() {
        return runtimeFabric8Version;
    }

    public static Version loadFromProperties() {
        final var sdkVersion = Utils.VERSION;

        return new Version(sdkVersion.getCommit(), sdkVersion.getBuiltTime(),
                Versions.BUILD_VERSION,
                Versions.COMMIT,
                Versions.BRANCH,
                io.fabric8.kubernetes.client.Version.clientVersion(),
                Versions.BUILD_TIME);
    }
}
