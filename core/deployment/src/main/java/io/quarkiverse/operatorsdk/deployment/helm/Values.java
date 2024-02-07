package io.quarkiverse.operatorsdk.deployment.helm;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

public class Values {

    private String watchNamespaces = WATCH_ALL_NAMESPACES;

    public String getWatchNamespaces() {
        return watchNamespaces;
    }

    public Values setWatchNamespaces(String watchNamespaces) {
        this.watchNamespaces = watchNamespaces;
        return this;
    }
}
