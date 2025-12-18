package io.quarkiverse.operatorsdk.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.operatorsdk.test.sources.External;
import io.quarkiverse.operatorsdk.test.sources.ExternalResourceDependent;
import io.quarkiverse.operatorsdk.test.sources.ResourceIDReconciler;
import io.quarkus.test.QuarkusProdModeTest;

public class FailOnMissingResourceIDExternalResourceTest {

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .assertBuildException(throwable -> {
                Throwable cause = throwable.getCause().getCause();
                Assertions.assertEquals(IllegalStateException.class, cause.getClass());
                Assertions.assertEquals("DependentResource 'io.quarkiverse.operatorsdk.test.sources.ExternalResourceDependent' "
                        + "is extending AbstractExternalDependentResource but the associated resource type 'java.lang.Void' does not implement "
                        + "ResourceIDProvider. Please implement ResourceIDProvider or provide a custom ResourceIDMapper implementation.",
                        cause.getMessage());
            })
            .withApplicationRoot((jar) -> jar
                    .addClasses(ResourceIDReconciler.class, External.class,
                            ExternalResourceDependent.class, Void.class));

    @Test
    public void shouldFailOnMissingResourceIDExternalResource() {
        Assertions.fail();
    }
}
