package io.quarkiverse.operatorsdk.runtime;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import io.javaoperatorsdk.operator.Operator;

@Readiness
@ApplicationScoped
public class OperatorHealthCheck implements HealthCheck {

    public static final String HEALTH_CHECK_NAME = "Quarkus Operator SDK health check";
    public static final String OK = "OK";
    @Inject
    Operator operator;

    @Override
    public HealthCheckResponse call() {
        final var runtimeInfo = operator.getRuntimeInfo();
        if (runtimeInfo.isStarted()) {
            final var response = HealthCheckResponse.named(HEALTH_CHECK_NAME);
            final boolean[] healthy = { true };
            runtimeInfo.getRegisteredControllers().forEach(rc -> {
                final var name = rc.getConfiguration().getName();
                final var unhealthy = rc.getControllerHealthInfo().unhealthyEventSources();
                if (unhealthy.isEmpty()) {
                    response.withData(name, OK);
                } else {
                    healthy[0] = false;
                    response
                            .withData(name, "unhealthy: " + String.join(", ", unhealthy.keySet()));
                }
            });
            if (healthy[0]) {
                response.up();
            } else {
                response.down();
            }
            return response.build();
        }
        return HealthCheckResponse.down(HEALTH_CHECK_NAME);
    }
}
