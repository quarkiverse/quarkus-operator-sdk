package io.quarkiverse.operatorsdk.runtime.devui;

import java.util.Collection;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.operatorsdk.runtime.devconsole.ControllerInfo;
import io.quarkiverse.operatorsdk.runtime.devconsole.ControllersSupplier;

@ApplicationScoped
public class JSONRPCService {
    private final ControllersSupplier supplier = new ControllersSupplier();

    @SuppressWarnings("rawtypes")
    public Collection<ControllerInfo> getControllers() {
        return supplier.get();
    }

    @SuppressWarnings("unused")
    public int controllersCount() {
        return supplier.count();
    }
}
