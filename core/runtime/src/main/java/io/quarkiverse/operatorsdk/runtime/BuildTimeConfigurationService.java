package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.quarkus.runtime.annotations.IgnoreProperty;

public class BuildTimeConfigurationService implements ConfigurationService {
    private final Version version;
    private final CRDGenerationInfo crdInfo;
    private final boolean startOperator;
    private final boolean closeClientOnStop;
    private final boolean stopOnInformerErrorDuringStartup;
    private final boolean enableSSA;
    private final List<String> leaderElectionActivationProfiles;

    public BuildTimeConfigurationService(Version version, CRDGenerationInfo crdInfo, boolean startOperator,
            boolean closeClientOnStop, boolean stopOnInformerErrorDuringStartup, boolean enableSSA,
            List<String> leaderElectionActivationProfiles) {
        this.version = version;
        this.crdInfo = crdInfo;
        this.startOperator = startOperator;
        this.closeClientOnStop = closeClientOnStop;
        this.stopOnInformerErrorDuringStartup = stopOnInformerErrorDuringStartup;
        this.enableSSA = enableSSA;
        this.leaderElectionActivationProfiles = leaderElectionActivationProfiles;
    }

    @Override
    public <R extends HasMetadata> ControllerConfiguration<R> getConfigurationFor(Reconciler<R> reconciler) {
        throw new UnsupportedOperationException();
    }

    @Override
    @IgnoreProperty
    public Set<String> getKnownReconcilerNames() {
        throw new UnsupportedOperationException();
    }

    public Version getVersion() {
        return version;
    }

    public CRDGenerationInfo getCrdInfo() {
        return crdInfo;
    }

    public boolean isStartOperator() {
        return startOperator;
    }

    public boolean isCloseClientOnStop() {
        return closeClientOnStop;
    }

    public boolean isStopOnInformerErrorDuringStartup() {
        return stopOnInformerErrorDuringStartup;
    }

    public boolean isEnableSSA() {
        return enableSSA;
    }

    public boolean activateLeaderElection(List<String> activeProfiles) {
        return activeProfiles.stream().anyMatch(leaderElectionActivationProfiles::contains);
    }

    public List<String> getLeaderElectionActivationProfiles() {
        return leaderElectionActivationProfiles;
    }
}
