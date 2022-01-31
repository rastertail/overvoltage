package net.rastertail.overvoltage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.concurrent.Callable;
import javax.security.auth.login.LoginException;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main class */
@Command(name = "overvoltage", mixinStandardHelpOptions = true)
public class Overvoltage implements Callable<Integer> {
    /** Logger for this class */
    private final Logger LOG = LoggerFactory.getLogger(Overvoltage.class);

    /** Whether or not to force reindexing the SID database */
    @Option(names = {"-r", "--reindex"}, description = "Force reindex the SID database")
    private boolean reindex;

    /** Whether or not to update Discord slash commands */
    @Option(names = {"-u", "--update-commands"}, description = "Update Discord slash commands")
    private boolean updateCommands;

    /**
     * Run the bot
     *
     * @return status code
     */
    @Override
    public Integer call() {
        try {
            // Read configuration environment variables
            String bot_token = System.getenv("BOT_TOKEN");
            String data_dir = System.getenv("DATA_DIR");
            String hvsc_path = System.getenv("HVSC_PATH");

            // Load SID database
            Directory index = FSDirectory.open(Paths.get(data_dir, "index"));
            SidDatabase sidDb = new SidDatabase(Paths.get(hvsc_path), index, this.reindex);

            // Connect to Discord
            Bot bot = new Bot(sidDb);
            JDA jda = JDABuilder.createLight(bot_token, EnumSet.noneOf(GatewayIntent.class))
                .addEventListeners(bot)
                .build();

            // Potentially update slash commands
            if (this.updateCommands) {
                bot.updateCommands(jda);
            }
        } catch (Exception e) {
            LOG.error("Uncaught exception: {}", e);
            return -1;
        }

        return 0;
    }

    /**
     * Start the bot
     *
     * @param args commandline arguments
     */
    public static void main(String[] args) {
        Integer exitCode = new CommandLine(new Overvoltage()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
