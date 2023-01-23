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
import io.smallrye.config.Expressions;
import io.smallrye.config.common.utils.StringUtil;

public class RuntimeConfigurationUtils {
    private static final ExpressionConfigSourceInterceptor RESOLVER = new ExpressionConfigSourceInterceptor();
    private final static Converter<HashSet<String>> converter = Converters.newCollectionConverter(
            Converters.getImplicitConverter(String.class), HashSet::new);
    private final static Config config = ConfigProvider.getConfig();
    private static final String QUARKUS_OPERATOR_SDK_CONTROLLERS = "quarkus.operator-sdk.controllers.";
    private static final String NAMESPACES = ".namespaces";

    public static Set<String> namespacesFromConfigurationFor(String controllerName) {
        final var propName = namespacePropertyKey(controllerName);

        // todo: might need to check several alternatives here :(
        // first check system properties
        final var envOrSysVarName = StringUtil.replaceNonAlphanumericByUnderscores(propName.toUpperCase());
        var namespaces = System.getProperty(envOrSysVarName);
        if (namespaces != null && !namespaces.isBlank()) {
            return toSet(namespaces);
        }

        // then check env variables
        namespaces = System.getenv(envOrSysVarName);
        if (namespaces != null && !namespaces.isBlank()) {
            return toSet(namespaces);
        }

        // finally check if we have a property for the namespaces
        var configValue = config.getConfigValue(propName);
        namespaces = Expressions.withoutExpansion(configValue::getRawValue);
        if (namespaces != null && !namespaces.isBlank()) {
            return toSet(namespaces);
        }

        return null;
    }

    private static Set<String> toSet(String namespaces) {
        return converter.convert(namespaces).stream().map(String::trim).collect(Collectors.toSet());
    }

    // todo: remove, use {@link #expandedValueFrom} when it actually works
    public static String expandedValueFrom2(String unexpanded) {
        if (unexpanded.startsWith("${")) {
            final var substring = unexpanded.substring(2, unexpanded.length() - 1);
            var expanded = System.getProperty(substring);
            if (expanded != null) {
                return expanded;
            }
            return System.getenv(substring);
        } else {
            return unexpanded;
        }
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

    public static String namespacePropertyKey(String controllerName) {
        return QUARKUS_OPERATOR_SDK_CONTROLLERS + controllerName + NAMESPACES;
    }
}
