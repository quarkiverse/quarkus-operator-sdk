/**
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.quarkiverse.operatorsdk.runtime;

import java.util.Optional;

import org.jboss.logging.Logger;

import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class GradualRetryResolver {
    private static final Logger log = Logger.getLogger(GradualRetryResolver.class.getName());

    public static Optional<ExternalGradualRetryConfiguration> gradualRetryIfConfigurationExists(
            QuarkusControllerConfiguration<?> c,
            ExternalGradualRetryConfiguration retry) {
        if (retry != null) {
            final Class<? extends Retry> retryClass = c.getRetryClass();
            if (!GenericRetry.class.equals(retryClass)) {
                log.warn(
                        "Retry configuration in application.properties is only appropriate when using the GenericRetry implementation, yet your Reconciler is configured to use "
                                + retryClass.getName()
                                + " as Retry implementation. Configuration from application.properties will therefore be ignored.");
                return Optional.empty();
            }

            return Optional.of(retry);
        }
        return Optional.empty();
    }
}
