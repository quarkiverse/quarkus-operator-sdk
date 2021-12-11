package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;

@ControllerConfiguration(name = ConfiguredReconciler.NAME, namespaces = "foo")
public class ConfiguredReconciler extends AbstractReconciler<ChildTestResource2> {

    public static final String NAME = "annotation";
}
