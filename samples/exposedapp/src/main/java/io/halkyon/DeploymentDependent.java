package io.halkyon;

import static io.halkyon.ExposedAppReconciler.LABELS_CONTEXT_KEY;
import static io.halkyon.ExposedAppReconciler.createMetadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class DeploymentDependent extends CRUDKubernetesDependentResource<Deployment, ExposedApp>
        implements Matcher<Deployment, ExposedApp> {

    // todo: automatically generate
    public DeploymentDependent() {
        super(Deployment.class);
    }

    @SuppressWarnings("unchecked")
    public Deployment desired(ExposedApp exposedApp, Context context) {
        final var labels = (Map<String, String>) context.managedWorkflowAndDependentResourceContext()
                .getMandatory(LABELS_CONTEXT_KEY, Map.class);
        final var name = exposedApp.getMetadata().getName();
        final var spec = exposedApp.getSpec();
        final var imageRef = spec.getImageRef();
        final var env = spec.getEnv();

        var containerBuilder = new DeploymentBuilder()
                .withMetadata(createMetadata(exposedApp, labels))
                .withNewSpec()
                .withNewSelector().withMatchLabels(labels).endSelector()
                .withNewTemplate()
                .withNewMetadata().withLabels(labels).endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(name).withImage(imageRef);

        // add env variables
        if (env != null) {
            env.forEach((key, value) -> containerBuilder.addNewEnv()
                    .withName(key.toUpperCase())
                    .withValue(value)
                    .endEnv());
        }

        return containerBuilder
                .addNewPort()
                .withName("http").withProtocol("TCP").withContainerPort(8080)
                .endPort()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    @Override
    public Result<Deployment> match(Deployment actual, ExposedApp primary, Context<ExposedApp> context) {
        final var desiredSpec = primary.getSpec();
        final var container = actual.getSpec().getTemplate().getSpec().getContainers()
                .stream()
                .findFirst();
        return Result.nonComputed(container.map(c -> c.getImage().equals(desiredSpec.getImageRef())
                && desiredSpec.getEnv().equals(convert(c.getEnv()))).orElse(false));
    }

    private Map<String, String> convert(List<EnvVar> envVars) {
        final var result = new HashMap<String, String>(envVars.size());
        envVars.forEach(e -> result.put(e.getName(), e.getValue()));
        return result;
    }
}
