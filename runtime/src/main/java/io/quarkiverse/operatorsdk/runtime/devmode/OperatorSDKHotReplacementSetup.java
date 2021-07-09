package io.quarkiverse.operatorsdk.runtime.devmode;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.quarkus.dev.spi.HotReplacementContext;
import io.quarkus.dev.spi.HotReplacementSetup;

public class OperatorSDKHotReplacementSetup implements HotReplacementSetup {
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void setupHotDeployment(HotReplacementContext context) {
        executor.scheduleAtFixedRate(() -> {
            try {
                context.doScan(false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }
}
