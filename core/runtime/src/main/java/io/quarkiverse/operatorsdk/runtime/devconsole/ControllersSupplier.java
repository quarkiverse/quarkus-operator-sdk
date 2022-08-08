package io.quarkiverse.operatorsdk.runtime.devconsole;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.processing.Controller;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkus.arc.Arc;

@SuppressWarnings("rawtypes")
public class ControllersSupplier implements Supplier<Collection<ControllerInfo>> {

    private static final Logger log = LoggerFactory.getLogger(ControllersSupplier.class);

    @Override
    public Collection<ControllerInfo> get() {
        try (final var operatorHandle = Arc.container().instance(Operator.class)) {
            return operatorHandle.get()
                    .getRegisteredControllers().stream()
                    .map(rc -> new ControllerInfo<>((Controller<? extends HasMetadata>) rc))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Couldn't retrieve controllers information because " + QuarkusConfigurationService.class.getSimpleName()
                    + " is not available", e);
            return Collections.emptyList();
        }
    }
}
