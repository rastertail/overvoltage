package net.rastertail.overvoltage;

import java.util.ArrayList;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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

    /** Slash command handler */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent ev) {
        // Only allow commands from guilds
        if (ev.getGuild() == null) {
            return;
        }

        try {
            // Dispatch event handler
            switch (ev.getName()) {
                case "play":
                    ArrayList<SidDatabase.SidInfo> results
                        = this.sidDb.search(ev.getOption("query").getAsString());

                    StringBuilder b = new StringBuilder();
                    for (SidDatabase.SidInfo info : results) {
                        b.append(info.artist);
                        b.append(" - ");
                        b.append(info.title);
                        b.append(" (");
                        b.append(info.released);
                        b.append(")\n");
                    }

                    ev.reply(b.toString()).queue();

                    break;
                default:
                    ev.reply("unimplemented").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            ev.reply(e.toString()).setEphemeral(true).queue();
        }
    }
}
