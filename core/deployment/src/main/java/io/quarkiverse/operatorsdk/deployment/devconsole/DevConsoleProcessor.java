package io.quarkiverse.operatorsdk.deployment.devconsole;

import io.quarkiverse.operatorsdk.runtime.devconsole.ControllersSupplier;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.devconsole.spi.DevConsoleRuntimeTemplateInfoBuildItem;

public class DevConsoleProcessor {

    @SuppressWarnings("unused")
    @BuildStep(onlyIf = IsDevelopment.class)
    public DevConsoleRuntimeTemplateInfoBuildItem exposeControllerConfigurations(
            CurateOutcomeBuildItem curateOutcomeBI) {
        return new DevConsoleRuntimeTemplateInfoBuildItem("controllers", new ControllersSupplier(), this.getClass(),
                curateOutcomeBI);
    }
}
