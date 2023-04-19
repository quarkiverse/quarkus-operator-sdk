package io.quarkiverse.operatorsdk.runtime.devui;

import java.util.Collection;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.operatorsdk.runtime.devconsole.ControllerInfo;
import io.quarkiverse.operatorsdk.runtime.devconsole.ControllersSupplier;

@ApplicationScoped
public class JSONRPCService {

    public Collection<ControllerInfo> getControllers() {
        return new ControllersSupplier().get();
    }
}
