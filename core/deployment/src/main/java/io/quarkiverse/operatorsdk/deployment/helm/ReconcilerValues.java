package io.quarkiverse.operatorsdk.deployment.helm;

public class ReconcilerValues {

    private String name;
    private String resource;

    public String getName() {
        return name;
    }

    public ReconcilerValues setName(String name) {
        this.name = name;
        return this;
    }

    public String getResource() {
        return resource;
    }

    public ReconcilerValues setResource(String resource) {
        this.resource = resource;
        return this;
    }
}
