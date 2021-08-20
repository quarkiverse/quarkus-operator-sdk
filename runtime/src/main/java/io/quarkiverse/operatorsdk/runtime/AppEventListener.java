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
        if (configurationService.getVersion() instanceof Version) {
            final var version = ((Version) configurationService.getVersion());
            final var branch = !version.getExtensionBranch().equals(Version.UNKNOWN)
                    ? " on branch: " + version.getExtensionBranch()
                    : "";
            log.info("Quarkus Java Operator SDK extension {} (commit: {}{})", version.getExtensionVersion(),
                    version.getExtensionCommit(), branch);
        }
        operator.start();
    }

    public void onShutdown(@Observes ShutdownEvent event) {
        log.info("Quarkus Java Operator SDK extension is shutting down");
        operator.close();
    }
}
