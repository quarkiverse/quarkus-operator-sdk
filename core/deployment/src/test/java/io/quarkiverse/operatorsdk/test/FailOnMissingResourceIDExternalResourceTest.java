package io.quarkiverse.operatorsdk.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.operatorsdk.test.sources.ExternalDependentResourceMissingResourceID;
import io.quarkiverse.operatorsdk.test.sources.TestCR;
import io.quarkiverse.operatorsdk.test.sources.TestReconciler;
import io.quarkus.test.QuarkusProdModeTest;

public class FailOnMissingResourceIDExternalResourceTest {

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setExpectedException(IllegalStateException.class)
            .withApplicationRoot((jar) -> jar
                    .addClasses(TestReconciler.class, TestCR.class,
                            ExternalDependentResourceMissingResourceID.class));

    @Test
    public void shouldFailOnMissingResourceID() {
        // should fail with deployment exception
        Assertions.fail();
    }

}
