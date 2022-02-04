package net.rastertail.overvoltage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

import libsidplay.common.CPUClock;
import libsidplay.common.EventScheduler;
import libsidplay.config.IAudioSection;
import sidplay.audio.AudioDriver;

/** A JSIDPlay2 audio driver that writes to a ring buffer of audio buffers */
public class BufferDriver implements AudioDriver {
    /** Length of the internal driver buffer in bytes */
    private int bufferLength;

    /** Internal driver buffer */
    private ByteBuffer buffer;

    /** Ring buffer of audio buffers */
    private byte[][] bufferRing;

    /** Ring buffer read pointer */
    private AtomicInteger readPtr;

    /** Ring buffer write pointer */
    private AtomicInteger writePtr;

    /** How many new buffers are present? */
    private AtomicInteger availLength;

    /**
     * Construct a new driver
     *
     * @param ringSize the size of the internal ring buffer
     */
    public BufferDriver(int ringSize) {
        this.bufferRing = new byte[ringSize][];
        this.readPtr = new AtomicInteger(0);
        this.writePtr = new AtomicInteger(0);
        this.availLength = new AtomicInteger(0);
    }

    /**
     * Open this audio driver
     *
     * @param audioSection audio configuration
     * @param recordingFilename unused
     * @param cpuClock unused
     * @param context unused
     */
    @Override
    public void open(
        IAudioSection audioSection,
        String recordingFilename,
        CPUClock cpuClock,
        EventScheduler context
    ) {
        // Initialize internal driver buffer
        int channels = 2;
        int bufferFrames = audioSection.getAudioBufferSize();

        this.bufferLength = bufferFrames * Short.BYTES * channels;
        this.buffer = ByteBuffer.allocate(this.bufferLength)
            .order(ByteOrder.BIG_ENDIAN);

        // Initialize output buffers
        for (int i = 0; i < this.bufferRing.length; i++) {
            this.bufferRing[i] = new byte[this.bufferLength];
        }
    }

    /**
     * Close this audio driver
     *
     * Does not actually do anything
     */
    @Override
    public void close() {}

    /** Get if this driver is recording */
    @Override
    public boolean isRecording() {
        return false;
    }

    /**
     * Write the internal audio buffer out to the driver's ring buffer
     */
    @Override
    public void write() {
        // Atomically update the write pointer
        int ptr = this.writePtr.getAndUpdate(p -> (p + 1) % this.bufferRing.length);

        // Copy internal buffer into current write buffer
        for (int i = 0; i < this.bufferLength; i++) {
            this.bufferRing[ptr][i] = this.buffer.get(i);
        }

        // Update available buffer length
        this.availLength.getAndIncrement();
    }

    /** Get the internal audio buffer */
    @Override
    public ByteBuffer buffer() {
        return this.buffer;
    }

    /** Read out a buffer from the driver's ring buffer */
    public byte[] read() {
        // Atomically update the read pointer
        int ptr = this.readPtr.getAndUpdate(p -> (p + 1) % this.bufferRing.length);

        byte[] buf = this.bufferRing[ptr];

        // Update available buffer length
        this.availLength.getAndDecrement();

        return buf;
    }

    /** Get if the driver's ring buffer has any new data available */
    public boolean hasData() {
        return this.availLength.get() > 0;
    }

    /** Get if the driver's ring buffer is full or not */
    public boolean full() {
        return this.availLength.get() >= this.bufferRing.length;
    }
}
