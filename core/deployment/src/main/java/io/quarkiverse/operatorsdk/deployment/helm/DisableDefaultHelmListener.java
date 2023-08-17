package io.quarkiverse.operatorsdk.deployment.helm;

import java.util.HashMap;
import java.util.Map;

import io.dekorate.WithSession;
import io.dekorate.kubernetes.config.BaseConfigFluent;
import io.dekorate.kubernetes.config.Configurator;

/**
 * Used to disable default Dekorate Helm chart generator, which would get automatically triggered by depending on the Dekorate
 * Helm annotations and the Quarkus Kubernetes extension.
 */
public class DisableDefaultHelmListener extends Configurator<BaseConfigFluent<?>> implements WithSession {
    @Override
    public void visit(BaseConfigFluent<?> baseConfigFluent) {
        Map<String, Object> helmConfig = new HashMap<>();
        helmConfig.put("enabled", "false");

        Map<String, Object> config = new HashMap<>();
        config.put("helm", helmConfig);

        WithSession.super.getSession().addPropertyConfiguration(config);
    }
}
