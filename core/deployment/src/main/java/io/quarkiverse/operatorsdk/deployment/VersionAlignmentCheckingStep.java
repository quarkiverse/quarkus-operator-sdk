package io.quarkiverse.operatorsdk.deployment;

import java.util.Objects;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.semver4j.Semver;
import org.semver4j.Semver.VersionDiff;

import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.Version;
import io.quarkus.deployment.annotations.BuildStep;

public class VersionAlignmentCheckingStep {

    private static final Logger log = Logger.getLogger(VersionAlignmentCheckingStep.class);

    @BuildStep
    VersionBuildItem checkVersionsAlignment(BuildTimeOperatorConfiguration buildTimeConfiguration) {
        final var version = Version.loadFromProperties();

        final var runtimeQuarkusVersion = io.quarkus.builder.Version.getVersion();
        checkVersionCompatibility(buildTimeConfiguration, runtimeQuarkusVersion, version.getQuarkusVersion(), "Quarkus");
        final var runtimeFabric8Version = io.fabric8.kubernetes.client.Version.clientVersion();
        checkVersionCompatibility(buildTimeConfiguration, runtimeFabric8Version, version.getKubernetesClientVersion(),
                "JOSDK Fabric8 Kubernetes Client");
        String quarkusFabric8Version = io.quarkus.kubernetes.client.deployment.Versions.KUBERNETES_CLIENT;
        checkVersionCompatibility(buildTimeConfiguration, runtimeFabric8Version, quarkusFabric8Version,
                "Quarkus-provided Fabric8 Kubernetes Client");

        return new VersionBuildItem(version);
    }

    private void checkVersionCompatibility(BuildTimeOperatorConfiguration buildTimeConfiguration, String found, String expected,
            String name) {
        // optimize most common case
        if (Objects.equals(found, expected)) {
            return;
        }
        final var foundVersionOpt = getSemverFrom(found);
        final var expectedVersionOpt = getSemverFrom(expected);
        if (foundVersionOpt.isEmpty() || expectedVersionOpt.isEmpty()) {
            // abort version check if we couldn't parse the version for some reason as a version check should not prevent the rest of the processing to proceed
            return;
        }
        final var foundVersion = foundVersionOpt.get();
        final var expectedVersion = expectedVersionOpt.get();
        if (!expectedVersion.equals(foundVersion)) {
            String message = "Mismatched " + name + " version found: \"" + found + "\", expected: \"" + expected
                    + "\"";
            if (buildTimeConfiguration.failOnVersionCheck) {
                throw new RuntimeException(message);
            } else {
                final var diff = expectedVersion.diff(foundVersion);
                if (diff.compareTo(VersionDiff.MINOR) >= 0) {
                    log.warn(message
                            + " by at least a minor version and things might not work as expected.");
                } else {
                    log.debug(message);
                }
            }
        }
    }

    private static Optional<Semver> getSemverFrom(String version) {
        try {
            return Optional.of(Semver.coerce(version));
        } catch (Exception e) {
            log.warn("Couldn't convert version " + version);
        }
        return Optional.empty();
    }
}
