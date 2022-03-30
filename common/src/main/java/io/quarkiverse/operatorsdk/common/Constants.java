package io.quarkiverse.operatorsdk.common;

import org.jboss.jandex.DotName;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.dependent.DependentResourceManager;

public class Constants {
    private Constants() {
    }

    public static final DotName RECONCILER = DotName.createSimple(Reconciler.class.getName());
    public static final DotName CONTROLLER = DotName.createSimple(Controller.class.getName());
    public static final DotName DEPENDENT_RESOURCE_MANAGER = DotName.createSimple(
            DependentResourceManager.class.getName());
    public static final DotName CUSTOM_RESOURCE = DotName.createSimple(CustomResource.class.getName());
    public static final DotName CONTROLLER_CONFIGURATION = DotName.createSimple(ControllerConfiguration.class.getName());
}
