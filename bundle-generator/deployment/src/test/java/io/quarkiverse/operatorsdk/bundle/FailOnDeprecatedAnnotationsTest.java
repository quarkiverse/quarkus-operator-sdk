package io.quarkiverse.operatorsdk.bundle;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata;
import io.quarkiverse.operatorsdk.bundle.runtime.SharedCSVMetadata;
import io.quarkiverse.operatorsdk.bundle.sources.*;
import io.quarkus.test.QuarkusProdModeTest;

public class FailOnDeprecatedAnnotationsTest {

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName("deprecated-annotations")
            .assertBuildException(e -> {
                // exception should be a runtime exception wrapping a build exception wrapping our own exception
                assertInstanceOf(IllegalStateException.class, e.getCause().getCause());
                final var message = e.getMessage();
                assertTrue(message.contains(DeprecatedReconciler.class.getName()));
                assertTrue(message.contains(CSVMetadata.class.getName()));
                assertTrue(message.contains(SharedCSVMetadata.class.getName()));
            })
            .withApplicationRoot((jar) -> jar
                    .addClasses(DeprecatedReconciler.class));

    @Test
    public void shouldFailWhenUsingDeprecatedMetadataAnnotations() {
        fail("Should have failed!");
    }

}
