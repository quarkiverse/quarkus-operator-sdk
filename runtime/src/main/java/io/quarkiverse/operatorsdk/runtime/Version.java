package io.quarkiverse.operatorsdk.runtime;

import java.util.Date;

import io.quarkus.runtime.annotations.RecordableConstructor;

/**
 * Re-publish with a recordable constructor so that quarkus can do its thing with it!
 */
public class Version extends io.javaoperatorsdk.operator.api.config.Version {

    @RecordableConstructor
    public Version(String sdkVersion, String commit, Date builtTime) {
        super(sdkVersion, commit, builtTime);
    }
}
