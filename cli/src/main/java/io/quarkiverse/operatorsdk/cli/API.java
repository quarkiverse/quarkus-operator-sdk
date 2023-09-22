package io.quarkiverse.operatorsdk.cli;

import static io.quarkiverse.operatorsdk.common.CLIConstants.*;

import java.util.concurrent.Callable;

import org.jboss.logging.Logger;

import io.quarkiverse.operatorsdk.common.Files;
import picocli.CommandLine;

@CommandLine.Command(name = "api", mixinStandardHelpOptions = true, description = API_DESCRIPTION)
public class API implements Callable<Integer> {
    private static final Logger log = Logger.getLogger(API.class);
    private static final String SHORT_PREFIX = "-";
    private static final String OPTION_PREFIX = "--";
    @CommandLine.Option(names = { SHORT_PREFIX + API_KIND_SHORT,
            OPTION_PREFIX + API_KIND }, description = API_KIND_DESCRIPTION, required = true)
    private String kind;

    @CommandLine.Option(names = { SHORT_PREFIX + API_GROUP_SHORT,
            OPTION_PREFIX + API_GROUP }, description = API_GROUP_DESCRIPTION, required = true)
    private String group;

    @CommandLine.Option(names = { SHORT_PREFIX + API_VERSION_SHORT,
            OPTION_PREFIX + API_VERSION }, description = API_VERSION_DESCRIPTION, required = true)
    private String version;

    private static final Files.MessageWriter writer = new Files.MessageWriter() {
        @Override
        public void write(String message, Exception e, boolean forError) {
            if (forError) {
                log.error(message, e);
            } else {
                log.info(message);
            }
        }
    };

    @Override
    public Integer call() throws Exception {
        return Files.generateAPIFiles(group, version, kind, writer) ? CommandLine.ExitCode.OK
                : CommandLine.ExitCode.SOFTWARE;
    }
}
