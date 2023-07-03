package io.quarkiverse.operatorsdk.runtime.devui;

import java.util.Collection;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.processing.Controller;
import io.quarkiverse.operatorsdk.runtime.devconsole.ControllerInfo;

@ApplicationScoped
public class JSONRPCService {
    @Inject
    Operator operator;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Collection<ControllerInfo> getControllers() {
        return operator.getRegisteredControllers().stream()
                .map(Controller.class::cast)
                .map(registeredController -> new ControllerInfo(registeredController))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unused")
    public int controllersCount() {
        return operator.getRegisteredControllersNumber();
    }
}
