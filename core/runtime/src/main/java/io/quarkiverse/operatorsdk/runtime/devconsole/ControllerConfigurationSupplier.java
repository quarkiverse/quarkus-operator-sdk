package io.quarkiverse.operatorsdk.runtime.devconsole;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkus.arc.Arc;

@SuppressWarnings("rawtypes")
public class ControllerConfigurationSupplier implements Supplier<Collection<ControllerConfiguration>> {

    private static final Logger log = LoggerFactory.getLogger(ControllerConfigurationSupplier.class);

    @Override
    public Collection<ControllerConfiguration> get() {
        try (final var service = Arc.container().instance(QuarkusConfigurationService.class)) {
            return service.get()
                    .controllerConfigurations().collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Couldn't retrieve controllers information because " + QuarkusConfigurationService.class.getSimpleName()
                    + " is not available", e);
            return Collections.emptyList();
        }
    }
}
