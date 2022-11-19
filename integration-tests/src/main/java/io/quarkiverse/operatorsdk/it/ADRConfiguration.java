package io.quarkiverse.operatorsdk.it;

public class ADRConfiguration {
    private final int value;

    public ADRConfiguration(int value) {
        this.value = value;
    }

    @SuppressWarnings("unused")
    public int getValue() {
        return value;
    }
}
