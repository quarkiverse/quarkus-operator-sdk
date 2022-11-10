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

    public static Set<String> stringPropValueAsSet(String propValue) {
        return converter.convert(propValue).stream().map(String::trim).collect(Collectors.toSet());
    }

    public static String[] stringPropValueAsArray(String propValue) {
        return converter.convert(propValue).stream().map(String::trim).distinct()
                .toArray(String[]::new);
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
