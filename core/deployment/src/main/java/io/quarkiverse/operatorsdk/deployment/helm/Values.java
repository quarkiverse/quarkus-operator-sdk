package io.quarkiverse.operatorsdk.deployment.helm;

import java.util.List;

public class Values {

    public static final String WATCH_ALL_NAMESPACES = "all-namespaces";

    private String watchNamespaces = WATCH_ALL_NAMESPACES;
    private String version;
    private String image;
    private List<ReconcilerValues> reconcilers;

    public List<ReconcilerValues> getReconcilers() {
        return reconcilers;
    }

    public Values setReconcilers(List<ReconcilerValues> reconcilers) {
        this.reconcilers = reconcilers;
        return this;
    }

    public String getWatchNamespaces() {
        return watchNamespaces;
    }

    public Values setWatchNamespaces(String watchNamespaces) {
        this.watchNamespaces = watchNamespaces;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public Values setVersion(String version) {
        this.version = version;
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
