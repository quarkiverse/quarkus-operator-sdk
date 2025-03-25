package io.quarkiverse.operatorsdk.deployment.devui;

import io.quarkiverse.operatorsdk.runtime.devui.JSONRPCService;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class DevUIProcessor {

    @SuppressWarnings("unused")
    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem create() {
        final var card = new CardPageBuildItem();
        card.addPage(Page.webComponentPageBuilder()
                .title("Controllers")
                .componentLink("qwc-qosdk-controllers.js")
                .dynamicLabelJsonRPCMethodName("controllersCount")
                .icon("font-awesome-solid:brain"));
        return card;
    }

    @SuppressWarnings("unused")
    @BuildStep
    JsonRPCProvidersBuildItem createJsonRPCService() {
        return new JsonRPCProvidersBuildItem(JSONRPCService.class);
    }
}
