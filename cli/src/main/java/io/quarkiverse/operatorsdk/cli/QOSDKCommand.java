package io.quarkiverse.operatorsdk.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(name = "qosdk", header = "Java Operator SDK commands", subcommands = API.class)
public class QOSDKCommand {
}
