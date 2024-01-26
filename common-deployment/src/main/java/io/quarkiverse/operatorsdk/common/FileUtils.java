package io.quarkiverse.operatorsdk.common;

import java.io.ByteArrayInputStream;
import java.io.File;

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

    /**
     * Unmarshal the given YAML or JSON to an object. The returned object will be a list if the input
     * is a YAML/JSON array.
     */
    public static Object unmarshalFrom(byte[] yamlOrJson) {
        return serializer.unmarshal(new ByteArrayInputStream(yamlOrJson));
    }

    /**
     * Serialize the given object to YAML. If the given object is a list, the returned YAML will be a
     * YAML array.
     */
    public static String asYaml(Object toSerialize) {
        return serializer.asYaml(toSerialize);
    }
}
