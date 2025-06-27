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
        card.addLibraryVersion("io.javaoperatorsdk", "operator-framework-core", "Java Operator SDK",
                "https://github.com/operator-framework/java-operator-sdk");
        card.setLogo("logo_dark.svg", "logo_light.svg");
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
