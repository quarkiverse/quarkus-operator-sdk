package io.quarkiverse.operatorsdk.deployment.devui.commands;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;

import io.quarkiverse.operatorsdk.runtime.Version;

@CommandDefinition(name = "versions", description = "Outputs QOSDK, Quarkus, Fabric8 and JOSDK versions, with which this QOSDK version was built")
@SuppressWarnings("rawtypes")
public class VersionsCommand implements Command {

    private final Version version;

    public VersionsCommand(Version version) {
        this.version = version;
    }

    @Override
    public CommandResult execute(CommandInvocation commandInvocation) {
        String output = "Targeted Quarkus version: " + version.getQuarkusVersion() + "\n" +
                "Fabric8 client version targeted by JOSDK: " + version.getKubernetesClientVersion() + "\n" +
                "QOSDK version: " + version.getExtensionCompleteVersion() + "\n" +
                "JOSDK version: " + version.getSdkVersion();
        commandInvocation.println(output);
        return CommandResult.SUCCESS;
    }
}
