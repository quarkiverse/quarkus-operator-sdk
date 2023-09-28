package io.quarkiverse.operatorsdk.runtime.config;

import io.quarkiverse.operatorsdk.runtime.Constants;
import io.smallrye.config.RelocateConfigSourceInterceptor;

public class QOSDKConfigRelocator extends RelocateConfigSourceInterceptor {

    public QOSDKConfigRelocator() {
        super(name -> name.startsWith(Constants.OLD_QOSDK_PREFIX)
                ? name.replace(Constants.OLD_QOSDK_PREFIX, Constants.NEW_QOSDK_PREFIX)
                : name);
    }
}
