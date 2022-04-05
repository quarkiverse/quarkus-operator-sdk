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
package io.quarkiverse.operatorsdk.samples.pingpong;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(namespaces = WATCH_CURRENT_NAMESPACE)
public class PongReconciler implements Reconciler<Pong> {

    @Override
    public UpdateControl<Pong> reconcile(Pong pong, Context<Pong> context) {
        Status status = pong.getStatus();
        if (status != null && Status.State.PROCESSED == status.getState()) {
            return UpdateControl.noUpdate();
        }

        pong.setStatus(new Status(Status.State.PROCESSED));
        return UpdateControl.updateStatus(pong);
    }
}
