package io.quarkiverse.operatorsdk.deployment.devui;

import io.quarkiverse.operatorsdk.runtime.devui.JSONRPCService;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class DevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem create() {
        final var card = new CardPageBuildItem();
        card.addPage(Page.webComponentPageBuilder()
                .title("controllers")
                .componentLink("qwc-qosdk-controllers.js")
                .icon("font-awesome-solid:brain"));
        return card;
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    JsonRPCProvidersBuildItem createJsonRPCService() {
        return new JsonRPCProvidersBuildItem(JSONRPCService.class);
    }
}
