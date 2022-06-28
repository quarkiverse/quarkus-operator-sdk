package io.quarkiverse.operatorsdk.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog.Language;
import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;
import io.quarkus.maven.ArtifactCoords;

public class OperatorSDKCodestartTest {

    @RegisterExtension
    static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .standaloneExtensionCatalog()
            .extension(ArtifactCoords
                    .fromString("io.quarkiverse.operatorsdk:quarkus-operator-sdk:" + System.getProperty("project.version")))
            .languages(Language.JAVA)
            .build();

    @Test
    void testContent() throws Throwable {
        codestartTest.checkGeneratedSource("org.acme.MyCustomResourceReconciler");
        codestartTest.checkGeneratedSource("org.acme.MyCustomResource");
        codestartTest.checkGeneratedSource("org.acme.MyCustomResourceSpec");
        codestartTest.checkGeneratedSource("org.acme.MyCustomResourceStatus");
    }

    @Test
    void testBuild() throws Throwable {
        codestartTest.buildAllProjects();
    }
}
