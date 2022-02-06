package net.rastertail.overvoltage;

import java.nio.ByteBuffer;

import builder.resid.ReSIDBuilder;
import libsidplay.HardwareEnsemble;
import libsidplay.config.IConfig;
import libsidplay.common.CPUClock;
import libsidplay.common.Event;
import libsidplay.common.SIDEmu;
import libsidplay.components.mos6510.MOS6510;
import libsidplay.sidtune.SidTune;

/** A minimal SID player based on JSIDPlay2 */
public class SidPlayer extends HardwareEnsemble {
    /** The SID emulation builder */
    private ReSIDBuilder sidBuilder;

    /** The audio driver to collect data in */
    private BufferDriver audioDriver;

    /** Construct a new SID player */
    public SidPlayer(IConfig config) {
        // Initialize a full C64 emulation
        super(config, MOS6510.class);

        // Save passed config
        this.config = config;

        // Create SID emulation builder
        this.sidBuilder = new ReSIDBuilder(
            this.c64.getEventScheduler(),
            this.config,
            this.c64.getClock()
        );

        // Construct, open, and register audio driver
        this.audioDriver = new BufferDriver(5);
        this.audioDriver.open(
            this.config.getAudioSection(),
            "",
            this.c64.getClock(),
            this.c64.getEventScheduler()
        );
        this.sidBuilder.setAudioDriver(this.audioDriver);
        this.sidBuilder.start();
    }

    /**
     * Initialize a song to be played
     *
     * @param tune the SID tune to play
     */
    public void play(SidTune tune) {
        // Update clock speed
        this.setClock(CPUClock.getCPUClock(this.config.getEmulationSection(), tune));

        // Reset machine
        this.reset();

        // Insert SID chips required for tune
        this.c64.insertSIDChips(
            (sidNum, sidEmu) -> {
                if (SidTune.isSIDUsed(this.config.getEmulationSection(), tune, sidNum)) {
                    return this.sidBuilder.lock(sidEmu, sidNum, tune);
                } else if (sidEmu != SIDEmu.NONE) {
                    this.sidBuilder.unlock(sidEmu);
                }
                return SIDEmu.NONE;
            },
            sidNum -> SidTune.getSIDAddress(config.getEmulationSection(), tune, sidNum)
        );

        // Update chip panning
        if (SidTune.isSIDUsed(this.config.getEmulationSection(), tune, 2)) {
            // 3SID: hard panning
            this.sidBuilder.setBalance(0, 0.0f);
            this.sidBuilder.setBalance(1, 1.0f);
            this.sidBuilder.setBalance(2, 0.5f);
        } else if (SidTune.isSIDUsed(this.config.getEmulationSection(), tune, 1)) {
            // 2SID: soft panning
            this.sidBuilder.setBalance(0, 0.25f);
            this.sidBuilder.setBalance(1, 0.75f);
        } else {
            // No panning
            this.sidBuilder.setBalance(0, 0.5f);
        }

        // Schedule tune autoload
        this.c64.getEventScheduler().schedule(new Event("Tune autoload") {
            @Override
            public void event() {
                // Assumes we are loading a PSID file with driver address
                Integer driverAddress = tune.placeProgramInMemory(c64.getRAM());
                c64.setPlayAddr(tune.getInfo().getPlayAddr());
                c64.getCPU().forcedJump(driverAddress);
            }
        }, SidTune.getInitDelay(tune));

        // Schedule SID mixing
        this.sidBuilder.start();
    }

    /** Render audio until the internal driver ring buffer is full */
    public void renderFull() throws InterruptedException {
        while (!this.audioDriver.full()) {
            this.c64.getEventScheduler().clock();
        }
    }

    /**
     * Get the internal audio driver
     *
     * @return the audio driver
     */
    public BufferDriver driver() {
        return this.audioDriver;
    }
}
