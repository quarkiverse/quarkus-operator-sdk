package io.quarkiverse.operatorsdk.deployment.devui.commands;

import java.util.List;

import org.aesh.command.*;
import org.aesh.command.invocation.CommandInvocation;

import io.quarkiverse.operatorsdk.runtime.Version;

@GroupCommandDefinition(name = "qosdk", description = "Quarkus Operator SDK Commands")
@SuppressWarnings("rawtypes")
public class ConsoleCommands implements GroupCommand {
    private final Version version;

    public ConsoleCommands(Version version) {
        this.version = version;
    }

    @Override
    public List<Command> getCommands() {
        return List.of(new VersionsCommand(version), new APICommand());
    }

    @Override
    public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
        commandInvocation.println(commandInvocation.getHelpInfo());
        return CommandResult.SUCCESS;
    }
}
