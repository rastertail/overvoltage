package net.rastertail.overvoltage;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import net.dv8tion.jda.api.audio.AudioSendHandler;
import libsidplay.common.SamplingMethod;
import libsidplay.common.SamplingRate;
import libsidplay.sidtune.SidTune;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sidplay.ini.IniConfig;

/** A Discord voice sender which sends SID music */
public class VoiceSender extends Thread implements AudioSendHandler {
    /** Logger for this class */
    private final Logger LOG = LoggerFactory.getLogger(VoiceSender.class);

    /** Internal SID player */
    private SidPlayer player;

    /** Queue of actions to run on the player in the render thread */
    private LinkedBlockingQueue<Consumer<SidPlayer>> actionQueue;

    /** Construct a new voice sender */
    public VoiceSender() {
        IniConfig config = new IniConfig();

        // Set up audio properties
        config.getAudioSection().setSamplingRate(SamplingRate.MEDIUM); // 48khz
        config.getAudioSection().setBufferSize(960); // 20ms
        config.getAudioSection().setAudioBufferSize(960);

        // Fix 6581 filter to be a bit more neutral
        config.getEmulationSection().setReSIDfpFilter6581("FilterTrurl6581R4AR_4486");
        config.getEmulationSection().setReSIDfpStereoFilter6581("FilterTrurl6581R4AR_4486");
        config.getEmulationSection().setReSIDfpThirdSIDFilter6581("FilterTrurl6581R4AR_4486");

        // Enable high quality resampling
        config.getAudioSection().setSampling(SamplingMethod.RESAMPLE);

        this.player = new SidPlayer(config);
        this.actionQueue = new LinkedBlockingQueue<Consumer<SidPlayer>>(3);

        // Start render thread
        new Thread(this).start();
    }

    /** Run the render thread */
    @Override
    public void run() {
        try {
            // Create initial buffer
            this.player.renderFull();

            // Accept further commands forever
            while (true) {
                this.actionQueue.take().accept(this.player);
            }
        } catch (InterruptedException e) {
            // Just warn on exceptions
            LOG.warn("Render thread died! {}", e);
        }
    }

    @Override
    public boolean canProvide() {
        return this.player.driver().hasData();
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        this.runInRenderThread(player -> {
            try {
                player.renderFull();
            } catch (InterruptedException e) {
                // Warn on exceptions
                LOG.warn("SID render interrupted! {}", e);
            }
        });
        return ByteBuffer.wrap(this.player.driver().read());
    }

    @Override
    public boolean isOpus() { return false; }

    /**
     * Run an action on the SID player in the render thread
     *
     * @param action the action to run
     */
    public void runInRenderThread(Consumer<SidPlayer> action) {
        try {
            this.actionQueue.put(action);
        } catch (InterruptedException e) {
            // Warn on exceptions
            LOG.warn("Render queue interrupted! {}", e);
        }
    }
}
