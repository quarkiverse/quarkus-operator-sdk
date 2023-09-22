package io.quarkiverse.operatorsdk.deployment.devui.commands;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

import io.quarkiverse.operatorsdk.common.CLIConstants;
import io.quarkiverse.operatorsdk.common.Files;

@CommandDefinition(name = "api", description = "Creates a Kubernetes API represented by a CustomResource and associated reconciler")
@SuppressWarnings("rawtypes")
public class APICommand implements Command {

    @Option(name = CLIConstants.API_KIND, shortName = CLIConstants.API_KIND_SHORT, description = CLIConstants.API_KIND_DESCRIPTION, required = true)
    private String kind;

    @Option(name = CLIConstants.API_GROUP, shortName = CLIConstants.API_GROUP_SHORT, description = CLIConstants.API_GROUP_DESCRIPTION, required = true)
    private String group;

    @Option(name = CLIConstants.API_VERSION, shortName = CLIConstants.API_VERSION_SHORT, description = CLIConstants.API_VERSION_DESCRIPTION, required = true)
    private String version;

    @Override
    public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
        return Files.generateAPIFiles(group, version, kind, new Files.MessageWriter() {
            @Override
            public void write(String message, Exception e, boolean forError) {
                commandInvocation.println(formatMessageWithException(message, e));
            }
        }) ? CommandResult.SUCCESS : CommandResult.FAILURE;
    }
}
