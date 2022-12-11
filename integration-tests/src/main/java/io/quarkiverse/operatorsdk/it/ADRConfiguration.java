package io.quarkiverse.operatorsdk.it;

import io.quarkus.runtime.annotations.RecordableConstructor;

public class ADRConfiguration {
    private final int value;

    @RecordableConstructor
    public ADRConfiguration(int value) {
        this.value = value;
    }

    @SuppressWarnings("unused")
    public int getValue() {
        return value;
    }
}
