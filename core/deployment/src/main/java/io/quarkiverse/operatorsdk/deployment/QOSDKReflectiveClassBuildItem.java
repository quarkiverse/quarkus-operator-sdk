package io.quarkiverse.operatorsdk.deployment;

import java.util.List;
import java.util.stream.Stream;

import io.quarkus.builder.item.MultiBuildItem;

final class QOSDKReflectiveClassBuildItem extends MultiBuildItem {

    private final List<String> classNamesToRegisterForReflection;

    public QOSDKReflectiveClassBuildItem(List<String> classNamesToRegisterForReflection) {
        this(classNamesToRegisterForReflection.toArray(new String[0]));
    }

    public QOSDKReflectiveClassBuildItem(String... classNameToRegisterForReflection) {
        this.classNamesToRegisterForReflection = List.of(classNameToRegisterForReflection);
    }

    public Stream<String> classNamesToRegisterForReflectionStream() {
        return classNamesToRegisterForReflection.stream();
    }
}
