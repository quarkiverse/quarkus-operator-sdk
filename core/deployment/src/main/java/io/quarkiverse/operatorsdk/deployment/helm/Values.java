package io.quarkiverse.operatorsdk.deployment.helm;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

public class Values {

    private String watchNamespaces = WATCH_ALL_NAMESPACES;
    private String image = "";

    public String getWatchNamespaces() {
        return watchNamespaces;
    }

    public Values setWatchNamespaces(String watchNamespaces) {
        this.watchNamespaces = watchNamespaces;
        return this;
    }

    public String getImage() {
        return image;
    }

    public Values setImage(String image) {
        this.image = image;
        return this;
    }
}
