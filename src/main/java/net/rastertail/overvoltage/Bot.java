package net.rastertail.overvoltage;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Discord event listener */
public class Bot extends ListenerAdapter {
    /** Logger for this class */
    private final Logger LOG = LoggerFactory.getLogger(Bot.class);

    /** The SID database to search from in commands */
    private SidDatabase sidDb;

    /**
     * Construct a new event listener
     *
     * @param sidDb the SID database to search from
     */
    public Bot(SidDatabase sidDb) {
        this.sidDb = sidDb;
    }

    /**
     * Update the bot's slash commands
     *
     * @param jda the JDA bot instance
     */
    public void updateCommands(JDA jda) {
        CommandListUpdateAction commands = jda.updateCommands();

        commands.addCommands(
            Commands.slash("play", "Search for a tune to play")
                .addOption(OptionType.STRING, "query", "What to search for", true)
        );

        commands.addCommands(
            Commands.slash("leave", "Leave the current voice channel")
        );

        commands.queue();
        LOG.info("Updated slash commands");
    }
}
