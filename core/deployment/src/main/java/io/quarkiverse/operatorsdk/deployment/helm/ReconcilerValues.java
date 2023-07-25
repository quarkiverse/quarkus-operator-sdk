package io.quarkiverse.operatorsdk.deployment.helm;

public class ReconcilerValues {

    private String name;
    private String resource;
    private String apiGroup;

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

    public String getApiGroup() {
        return apiGroup;
    }

    public ReconcilerValues setApiGroup(String apiGroup) {
        this.apiGroup = apiGroup;
        return this;
    }
}
