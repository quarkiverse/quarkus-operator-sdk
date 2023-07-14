package io.quarkiverse.operatorsdk.runtime;

import java.util.Collections;
import java.util.Set;

public final class Constants {
    private Constants() {
    }

    public static final Set<String> QOSDK_USE_BUILDTIME_NAMESPACES_SET = Collections
            .singleton(Constants.QOSDK_USE_BUILDTIME_NAMESPACES);

    public static final String QOSDK_USE_BUILDTIME_NAMESPACES = "QOSDK_USE_BUILDTIME_NAMESPACES";
}
