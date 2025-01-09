package io.quarkiverse.operatorsdk.common;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.Map;

import io.quarkus.qute.Qute;
import io.quarkus.qute.TemplateException;
import io.quarkus.qute.TemplateInstance;

public class Files {

    private static final String API_TEMPLATES_PATH = "templates/cli/api";
    private static final String ROOT_TARGET_DIR = "src/main/java/";

    private Files() {
    }

    public interface MessageWriter {
        default void write(String message) {
            write(message, null, false);
        }

        default void error(String message) {
            error(message, null);
        }

        default void error(String message, Exception e) {
            write(message, e, true);
        }

        default void write(String message, Exception e, boolean forError) {
            final var writer = forError ? System.err : System.out;
            writer.println(formatMessageWithException(message, e));
        }

        default String formatMessageWithException(String message, Exception e) {
            return message + (e != null ? ": " + exceptionAsString(e) : "");
        }
    }

    public static boolean generateAPIFiles(String group, String version, String kind, MessageWriter messager) {
        final var packageName = reversePeriodSeparatedString(group);
        final var finalKind = capitalizeIfNeeded(kind);

        final var targetDir = ROOT_TARGET_DIR + packageName.replace('.', '/') + "/";
        try {
            java.nio.file.Files.createDirectories(Paths.get(targetDir));
        } catch (IOException e) {
            messager.error("Couldn't create target directory " + targetDir, e);
            return false;
        }

        final var apiTemplatesDir = Thread.currentThread().getContextClassLoader().getResource(API_TEMPLATES_PATH);
        if (apiTemplatesDir == null) {
            messager.error("Couldn't find " + API_TEMPLATES_PATH + " directory in resources");
            return false;
        }

        // make sure we can get the files from a jar, which, for some reason, doesn't work directly
        // see https://stackoverflow.com/questions/22605666/java-access-files-in-jar-causes-java-nio-file-filesystemnotfoundexception
        final URI apiTemplatesDirURI;
        try {
            apiTemplatesDirURI = apiTemplatesDir.toURI();
        } catch (URISyntaxException e) {
            messager.error("Couldn't convert " + apiTemplatesDir + " path to URI", e);
            return false;
        }
        if ("jar".equals(apiTemplatesDirURI.getScheme())) {
            for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
                if (provider.getScheme().equalsIgnoreCase("jar")) {
                    try {
                        provider.getFileSystem(apiTemplatesDirURI);
                    } catch (FileSystemNotFoundException e) {
                        // if for some reason, there's no file system for our jar, initialize it first
                        try {
                            provider.newFileSystem(apiTemplatesDirURI, Collections.emptyMap());
                        } catch (IOException ex) {
                            messager.error("Couldn't initialize FileSystem instance to read resources", e);
                            return false;
                        }
                    }
                }
            }
        }

        final var data = Map.<String, Object> of("packageName", packageName,
                "group", group,
                "version", version,
                "kind", finalKind);

        final var apiTemplatesPath = Paths.get(apiTemplatesDirURI);
        try (var paths = java.nio.file.Files.walk(apiTemplatesPath)) {
            paths.filter(f -> java.nio.file.Files.isRegularFile(f) && f.getFileName().toString().endsWith(".qute"))
                    .forEach(path -> {
                        try {
                            final var templateAsString = java.nio.file.Files.readString(path);
                            final var templateInstance = Qute.fmt(templateAsString)
                                    .dataMap(data).instance();
                            generateFile(path, templateInstance, finalKind, targetDir, messager);
                        } catch (TemplateException e) {
                            throw new RuntimeException("Couldn't render " + path + " template", e.getCause());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            messager.error(e.getCause().getMessage(), e);
            return false;
        }

        return true;
    }

    private static String exceptionAsString(Exception e) {
        var stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    private static void generateFile(Path path, TemplateInstance templateInstance, String kind, String targetDir,
            MessageWriter messager)
            throws IOException {
        final var id = path.getFileName().toString();
        var className = getClassNameFor(id, kind);
        final var file = new File(targetDir + className + ".java");
        if (file.exists()) {
            throw new IllegalArgumentException(file + " already exists");
        }
        try (var writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
            writer.print(templateInstance.render());
        }
        messager.write("Generated " + file);
    }

    private static String getClassNameFor(String id, String kind) {
        id = id.substring(0, id.lastIndexOf('.'));
        return "resource".equals(id) ? kind : kind + capitalizeIfNeeded(id);
    }

    private static String reversePeriodSeparatedString(String input) {
        String[] splitString = input.split("\\.");
        StringBuilder sb = new StringBuilder();

        for (int i = splitString.length - 1; i >= 0; i--) {
            sb.append(splitString[i]);
            if (i > 0) {
                sb.append(".");
            }
        }

        return sb.toString();
    }

    private static String capitalizeIfNeeded(String input) {
        final var firstChar = input.charAt(0);
        return Character.isUpperCase(firstChar) ? input : Character.toUpperCase(firstChar) + input.substring(1);
    }

}
