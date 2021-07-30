package io.quarkiverse.operatorsdk.runtime;

import javax.enterprise.event.Observes;

import io.javaoperatorsdk.operator.Operator;
import io.quarkus.runtime.StartupEvent;

public class StartupListener {
    private final Operator operator;

    public StartupListener(Operator operator) {
        this.operator = operator;
    }

    public void onStartup(@Observes StartupEvent event) {
        operator.start();
    }
}
