package io.quarkiverse.operatorsdk.annotations;

/**
 * A marker interface used to identify classes bearing {@link CSVMetadata} that needs to be shared across reconcilers using the
 * same {@link CSVMetadata#bundleName()} attribute. Note that sharing metadata without using {@link SharedCSVMetadata} is not
 * allowed.
 */
public interface SharedCSVMetadata {
}
