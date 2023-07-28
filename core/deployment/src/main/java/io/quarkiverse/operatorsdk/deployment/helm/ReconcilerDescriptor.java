package io.quarkiverse.operatorsdk.deployment.helm;

public class ReconcilerDescriptor {

    private String name;
    private String resource;
    private String apiGroup;

    public String getName() {
        return name;
    }

    public ReconcilerDescriptor setName(String name) {
        this.name = name;
        return this;
    }

    public String getResource() {
        return resource;
    }

    public ReconcilerDescriptor setResource(String resource) {
        this.resource = resource;
        return this;
    }

    public String getApiGroup() {
        return apiGroup;
    }

    public ReconcilerDescriptor setApiGroup(String apiGroup) {
        this.apiGroup = apiGroup;
        return this;
    }
}
