package io.quarkiverse.operatorsdk.common;

import org.jboss.jandex.DotName;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.processing.ConfiguredController;

public class Constants {
    public static final DotName RESOURCE_CONTROLLER = DotName.createSimple(ResourceController.class.getName());
    public static final DotName CONFIGURED_CONTROLLER = DotName.createSimple(ConfiguredController.class.getName());
    public static final DotName CUSTOM_RESOURCE = DotName.createSimple(CustomResource.class.getName());
    public static final DotName CONTROLLER = DotName.createSimple(Controller.class.getName());
}
