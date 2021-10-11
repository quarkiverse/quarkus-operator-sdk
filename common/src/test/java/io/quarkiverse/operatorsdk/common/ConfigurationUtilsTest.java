package io.quarkiverse.operatorsdk.common;

import java.util.Optional;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.api.config.Utils;

public class ConfigurationUtilsTest {
    @Test
    void shouldUseSystemPropertyIfPresent() {
        if (Utils.isValidateCustomResourcesEnvVarSet()) {
            Assertions.assertEquals(Utils.shouldCheckCRDAndValidateLocalModel(),
                    ConfigurationUtils.shouldValidateCustomResources(Optional.empty(), false, null));
        }
    }

    @Test
    void shouldLogAWarningIfDeprecatedVersionIsUsed() {
        checkLogging(true, false);
        checkLogging(true, true);
        checkLogging(false, false);
        checkLogging(false, true);
    }

    private void checkLogging(boolean fromOld, boolean fromNew) {
        var logger = new TestLogger("test", fromOld, fromNew);
        ConfigurationUtils.shouldValidateCustomResources(Optional.of(fromOld), fromNew, logger);
        Assertions.assertTrue(logger.wasCalled);
    }

    private static class TestLogger extends Logger {
        private boolean wasCalled;
        private boolean fromOld;
        private boolean fromNew;

        protected TestLogger(String name, boolean fromOld, boolean fromNew) {
            super(name);
            this.fromOld = fromOld;
            this.fromNew = fromNew;
        }

        @Override
        protected void doLog(Level level, String s, Object o, Object[] objects, Throwable throwable) {
            Assertions.assertEquals(Level.WARN, level);
            Assertions.assertTrue(o instanceof String);
            var stringValue = (String) o;
            if (fromOld != fromNew) {
                Assertions.assertTrue(stringValue.contains("check-crd-and-validate-local-model with value '" + fromOld)
                        && stringValue.contains("crd.validate property value '" + fromNew));
            } else {
                Assertions.assertTrue(stringValue.contains("deprecated")
                        && !stringValue.contains("'" + fromOld)
                        && !stringValue.contains("'" + fromNew));
            }
            wasCalled = true;
        }

        @Override
        protected void doLogf(Level level, String s, String s1, Object[] objects, Throwable throwable) {
            Assertions.assertEquals(Level.WARN, level);
            wasCalled = true;
            Assertions.assertTrue(s1.contains("deprecated"));
        }

        @Override
        public boolean isEnabled(Level level) {
            return true;
        }
    }
}
