package io.quarkiverse.operatorsdk.it;

import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection // for proper serialization in native mode
public class NullItemStore implements ItemStore<ResourceQuota> {

    public static final String NAME = "NullItemStoreName";

    // so that it appears in the JSON configuration and we can check against it
    public String getName() {
        return NAME;
    }

    @Override
    public String getKey(ResourceQuota resourceQuota) {
        return null;
    }

    @Override
    public ResourceQuota put(String s, ResourceQuota resourceQuota) {
        return null;
    }

    @Override
    public ResourceQuota remove(String s) {
        return null;
    }

    @Override
    public Stream<String> keySet() {
        return null;
    }

    @Override
    public Stream<ResourceQuota> values() {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public ResourceQuota get(String s) {
        return null;
    }
}
