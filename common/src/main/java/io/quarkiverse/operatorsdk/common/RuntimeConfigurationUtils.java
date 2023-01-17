package io.quarkiverse.operatorsdk.common;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.Converter;

import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.Converters;
import io.smallrye.config.ExpressionConfigSourceInterceptor;

public class RuntimeConfigurationUtils {

    private static final ExpressionConfigSourceInterceptor RESOLVER = new ExpressionConfigSourceInterceptor();
    private final static Converter<HashSet<String>> converter = Converters.newCollectionConverter(
            Converters.getImplicitConverter(String.class), HashSet::new);
    private final static Config config = ConfigProvider.getConfig();
    private static final String QUARKUS_OPERATOR_SDK_CONTROLLERS = "quarkus.operator-sdk.controllers.";
    private static final String NAMESPACES = ".namespaces";

    public static Set<String> namespacesFromConfigurationFor(String controllerName) {
        // first check if we have a property for the namespaces
        var namespaces = getNamespacesFromConfigFor(controllerName);

        // check if the controller name is doubly quoted
        if (namespaces == null) {
            namespaces = getNamespacesFromConfigFor("\"" + controllerName + '"');
        }

        // check if the controller name is simply quoted
        if (namespaces == null) {
            namespaces = getNamespacesFromConfigFor("'" + controllerName + '\'');
        }

        if (namespaces != null) {
            // if we have a property, use it and convert it to a set of namespaces
            final var namespacesValue = namespaces.getValue();
            if (namespacesValue != null) {
                return stringPropValueAsSet(namespacesValue);
            }
        }

        return null;
    }

    private static org.eclipse.microprofile.config.ConfigValue getNamespacesFromConfigFor(String controllerName) {
        final var propName = QUARKUS_OPERATOR_SDK_CONTROLLERS + controllerName + NAMESPACES;
        return config.getConfigValue(propName);
    }

    public static Set<String> stringPropValueAsSet(String propValue) {
        return converter.convert(propValue).stream().map(String::trim).collect(Collectors.toSet());
    }

    public static String expandedValueFrom(String unexpandedValue) {
        final var context = new ConfigSourceInterceptorContext() {
            private boolean firstLookup = true;

            @Override
            public ConfigValue proceed(final String name) {
                if (firstLookup) {
                    firstLookup = false;
                    return ConfigValue.builder().withName(name).withValue(name).build();
                } else {
                    ConfigValue configValue = (ConfigValue) config.getConfigValue(name);
                    return configValue.getValue() == null ? null : configValue;
                }
            }

            @Override
            public Iterator<String> iterateNames() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Iterator<ConfigValue> iterateValues() {
                throw new UnsupportedOperationException();
            }
        };
        final var value = RESOLVER.getValue(context, unexpandedValue);
        return value.getValue() == null ? unexpandedValue : value.getValue();
    }
}
