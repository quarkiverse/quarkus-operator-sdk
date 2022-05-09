package io.quarkiverse.operatorsdk.runtime;

import javax.enterprise.event.Observes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.Operator;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

public class AppEventListener {
    private static final Logger log = LoggerFactory.getLogger(AppEventListener.class);
    private final Operator operator;
    private final QuarkusConfigurationService configurationService;

    public AppEventListener(Operator operator, QuarkusConfigurationService configurationService) {
        this.operator = operator;
        this.configurationService = configurationService;
    }

    public void onStartup(@Observes StartupEvent event) {
        if (configurationService.shouldStartOperator()) {
            if (operator.getRegisteredControllersNumber() > 0) {
                log.info("Starting operator.");
                operator.start();
            } else {
                log.warn("No Reconciler implementation was found so the Operator was not started.");
            }
        } else {
            log.warn("Operator was configured not to start automatically, call the start method to start it.");
        }
    }

    public void onShutdown(@Observes ShutdownEvent event) {
        log.info("Quarkus Java Operator SDK extension is shutting down.");
        operator.stop();
    }
}
