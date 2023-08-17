package io.quarkiverse.operatorsdk.runtime;

import java.io.File;

public class FileUtils {
    public static void ensureDirectoryExists(File dir) {
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IllegalArgumentException("Couldn't create " + dir.getAbsolutePath());
            }
        }
    }
}
