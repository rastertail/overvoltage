package net.rastertail.overvoltage;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import libsidplay.sidtune.SidTune;
import sidplay.ini.IniConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
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

    /**
     * Slash command handler
     *
     * @param ev slash command interaction event
     **/
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
                    // Perform search
                    ArrayList<SidDatabase.SidInfo> results
                        = this.sidDb.search(ev.getOption("query").getAsString());

                    if (results.size() == 0) {
                        // No results found
                        LOG.debug("Search yielded no results");
                        ev.reply("??? No results found!").queue();
                    } else if (results.size() == 1) {
                        // Exactly one result found
                        this.playTune(ev, results.get(0).path);
                    } else {
                        // Multiple results found
                        this.promptChoice(ev, results);
                    }

                    break;
                case "leave":
                    this.leave(ev);
                    break;
            }
        } catch (Exception e) {
            ev.reply(e.toString()).setEphemeral(true).queue();
        }
    }

    /**
     * Select menu interaction handler
     *
     * @param ev select menu interaction event
     **/
    @Override
    public void onSelectMenuInteraction(SelectMenuInteractionEvent ev) {
        // TODO Do not assume all menu interactions should lead to a tune playing
        this.playTune(ev, Paths.get(ev.getValues().get(0)));
    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent ev) {
        // Check if we are the one that disconnected
        if (ev.getMember().getId().equals(ev.getJDA().getSelfUser().getId())) {
            // Get audio manager to clean up resources
            AudioManager audioManager = ev.getGuild().getAudioManager();
            LOG.debug("Cleaning up {}...", ev.getGuild());

            // Drop voice handler
            audioManager.setSendingHandler(null);
        } else if (ev.getChannelLeft().getMembers().size() == 1) {
            // Disconnect from voice if we are the only one left
            AudioManager audioManager = ev.getGuild().getAudioManager();
            audioManager.closeAudioConnection();
        }
    }

    /**
     * Play a SID tune
     *
     * @param ev the event that prompted the tune to play
     * @param path the HVSC path to the tune
     */
    private void playTune(IReplyCallback ev, Path path) {
        LOG.debug("Playing SID tune from {}", path);

        try {
            // Load tune and extract info
            SidTune tune = this.sidDb.load(path);
            String[] tuneInfo = tune.getInfo().getInfoString().toArray(new String[] {});

            // Prepare tune for playback
            tune.getInfo().setSelectedSong(1);
            tune.prepare();

            // Find voice channel, bailing out if it does not exist
            Member member = ev.getMember();
            GuildVoiceState voiceState = member.getVoiceState();
            AudioChannel voiceChannel = voiceState.getChannel();
            if (voiceChannel == null) {
                ev.reply("??? You are not in a voice channel!").queue();
                return;
            }

            // Create guild voice sender if it does not yet exist
            Guild guild = voiceChannel.getGuild();
            AudioManager audioManager = guild.getAudioManager();
            if (audioManager.getSendingHandler() == null) {
                LOG.debug("Setting up on {}...", guild);
                VoiceSender sender = new VoiceSender();
                audioManager.setSendingHandler(sender);
            }

            // Connect to voice
            audioManager.openAudioConnection(voiceChannel);

            // Start playing tune
            ((VoiceSender) audioManager.getSendingHandler()).runInRenderThread(
                player -> player.play(tune)
            );

            // Send playback message
            ev.replyFormat(
                "???? Now playing **%s** by **%s**!",
                tuneInfo[0],
                tuneInfo[1]
            ).queue();
        } catch (Exception e) {
            LOG.error("Error starting SID playback: {}", e);
            ev.reply("??? An unexpected error occurred!").queue();
        }
    }

    /**
     * Prompt the user to select from a choice of multiple tunes
     *
     * @param ev slash command interaction event
     * @param choices the tunes to choose from
     **/
    private void promptChoice(
        SlashCommandInteractionEvent ev,
        ArrayList<SidDatabase.SidInfo> choices
    ) {
        LOG.debug("Search yielded multiple results... prompting for a choice!");

        // Build reply menu
        SelectMenu.Builder menu = SelectMenu.create("sid_chooser")
            .setPlaceholder("Make a choice...")
            .setRequiredRange(1, 1);

        for (SidDatabase.SidInfo choice : choices) {
            menu.addOption(
                choice.title,
                choice.path.toString(),
                choice.artist + " ??? " + choice.released
            );
        }

        // Send reply
        ev.reply("Please make a selection")
            .addActionRow(menu.build())
            .setEphemeral(true)
            .queue();
    }

    /**
     * Disconnect from voice and clean up
     *
     * @param ev the event that prompted this command
     */
    private void leave(IReplyCallback ev) {
        // Find voice channel, bailing out if it does not exist
        Member member = ev.getMember();
        GuildVoiceState voiceState = member.getVoiceState();
        AudioChannel voiceChannel = voiceState.getChannel();
        if (voiceChannel == null) {
            ev.reply("??? You are not in a voice channel!").queue();
            return;
        }

        // Check if we are in the same voice channel, or even in one at all
        Guild guild = voiceChannel.getGuild();
        AudioManager audioManager = guild.getAudioManager();
        AudioChannel connectedChannel = audioManager.getConnectedChannel();
        if (connectedChannel == null) {
            ev.reply("??? I am not in a voice channel!").queue();
            return;
        }
        if (!connectedChannel.equals(voiceChannel)) {
            ev.reply("??? Please join the same voice channel as me!").queue();
            return;
        }

        // Disconnect from voice
        audioManager.closeAudioConnection();

        // Confirm that the bot left
        ev.reply("???? Goodbye!").queue();
    }
}
