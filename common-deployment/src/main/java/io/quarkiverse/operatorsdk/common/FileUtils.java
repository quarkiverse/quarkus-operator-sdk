package io.quarkiverse.operatorsdk.common;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;

public class FileUtils {
    private final static KubernetesSerialization serializer = new KubernetesSerialization();

    public static void ensureDirectoryExists(File dir) {
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IllegalArgumentException("Couldn't create " + dir.getAbsolutePath());
            }
        }
    }

    public static List<HasMetadata> unmarshalFrom(byte[] yamlOrJson) {
        return serializer.unmarshal(new ByteArrayInputStream(yamlOrJson));
    }

    public static String asYaml(Object toSerialize) {
        return serializer.asYaml(toSerialize);
    }
}
