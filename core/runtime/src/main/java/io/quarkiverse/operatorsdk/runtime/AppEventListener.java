package io.quarkiverse.operatorsdk.runtime;

import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;

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

    public void onStartup(@Observes @Priority(Interceptor.Priority.LIBRARY_AFTER + 123) StartupEvent event) {
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
        if (configurationService.shouldStartOperator()) {
            log.info("Quarkus Java Operator SDK extension is shutting down. Is standard shutdown: {}",
                    event.isStandardShutdown());
            operator.stop();
        } else {
            log.warn("Operator was configured not to start automatically, call the stop method to stop it.");
        }
    }
}
