package io.quarkiverse.operatorsdk.runtime;

import java.util.concurrent.ExecutorService;

import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;

import org.jboss.logging.Logger;

import io.javaoperatorsdk.operator.Operator;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

public class AppEventListener {
    private static final Logger log = Logger.getLogger(AppEventListener.class);
    private final Operator operator;
    private final QuarkusConfigurationService configurationService;
    private final ExecutorService executor;

    // The ExecutorService is the default Quarkus managed ExecutorService that comes from the ArC extension
    public AppEventListener(Operator operator, QuarkusConfigurationService quarkusConfigurationService,
            ExecutorService executor) {
        this.operator = operator;
        this.configurationService = quarkusConfigurationService;
        this.executor = executor;
    }

    public void onStartup(@Observes @Priority(Interceptor.Priority.LIBRARY_AFTER + 123) StartupEvent event) {
        if (configurationService.isAsyncStart()) {
            // delegate the startup to a separate thread in order not to block other processing, e.g. HTTP server
            executor.execute(this::startOperator);
        } else {
            startOperator();
        }
    }

    public void onShutdown(@Observes ShutdownEvent event) {
        if (configurationService.shouldStartOperator()) {
            log.infof("Quarkus Java Operator SDK extension is shutting down. Is standard shutdown: %s",
                    event.isStandardShutdown());
            operator.stop();
        } else {
            log.warn("Operator was configured not to stop automatically, call the stop method to stop it.");
        }
    }

    private void startOperator() {
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
}
