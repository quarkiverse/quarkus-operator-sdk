package io.quarkiverse.operatorsdk.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class OperatorSDKProcessor {

  private static final String FEATURE = "operator-sdk";

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem(FEATURE);
  }
}
