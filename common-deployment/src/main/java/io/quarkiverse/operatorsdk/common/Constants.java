package io.quarkiverse.operatorsdk.common;

import org.jboss.jandex.DotName;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;
import io.javaoperatorsdk.operator.api.config.dependent.Configured;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.quarkiverse.operatorsdk.annotations.AdditionalRBACRules;
import io.quarkiverse.operatorsdk.annotations.RBACRule;

// todo: move to common-deployment module?
public class Constants {
    private Constants() {
    }

    public static final DotName RECONCILER = DotName.createSimple(Reconciler.class.getName());
    public static final DotName IGNORE_ANNOTATION = DotName.createSimple(Ignore.class.getName());
    public static final DotName CUSTOM_RESOURCE = DotName.createSimple(CustomResource.class.getName());
    public static final DotName HAS_METADATA = DotName.createSimple(HasMetadata.class.getName());
    public static final DotName CONTROLLER_CONFIGURATION = DotName.createSimple(ControllerConfiguration.class.getName());
    public static final DotName DEPENDENT_RESOURCE = DotName.createSimple(DependentResource.class.getName());
    public static final DotName CONFIGURED = DotName.createSimple(Configured.class.getName());
    public static final DotName ANNOTATION_CONFIGURABLE = DotName.createSimple(AnnotationConfigurable.class.getName());
    public static final DotName OBJECT = DotName.createSimple(Object.class.getName());
    public static final DotName ADDITIONAL_RBAC_RULES = DotName.createSimple(AdditionalRBACRules.class.getName());
    public static final DotName RBAC_RULE = DotName.createSimple(RBACRule.class.getName());
}
