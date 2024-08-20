package io.quarkiverse.operatorsdk.samples.pingpong;

import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.SharedCSVMetadata;

@CSVMetadata(bundleName = PingPongOperatorCSVMetadata.BUNDLE_NAME)
@SuppressWarnings("unused")
public class PingPongOperatorCSVMetadata implements SharedCSVMetadata {
    public static final String BUNDLE_NAME = "pingpong-operator";
}
