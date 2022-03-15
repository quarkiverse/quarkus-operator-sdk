package io.halkyon;

import java.util.Collections;
import java.util.Map;

public class ExposedAppSpec {

    // Add Spec information here
    private String imageRef;
    private Map<String, String> env;

    public String getImageRef() {
        return imageRef;
    }

    public void setImageRef(String imageRef) {
        this.imageRef = imageRef;
    }

    public Map<String, String> getEnv() {
        return env == null ? Collections.emptyMap() : env;
    }
}
