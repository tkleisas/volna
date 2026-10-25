package org.bokontep.wavesynth;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Sessionless RTP-MIDI sender (RFC 6295, no recovery journal).
 *
 * <p>Messages are batched for a few milliseconds and sent as RTP-MIDI
 * packets with 48 kHz timestamp units. This is the wire format YAWN's
 * RTP-MIDI receiver listens for. It intentionally omits AppleMIDI session
 * negotiation; both this client and the YAWN receiver accept sessionless
 * RTP-MIDI directly.</p>
 */
public final class RtpMidiClient {
    public interface StatusListener {
        void onStatus(String message, boolean error);
    }

    private static final int CLOCK_RATE = 48000;
    private static final int MAX_BATCH_MESSAGES = 16;
    private static final int MAX_MIDI_LIST = 900;

    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(2048);
    private final int ssrc;

    private volatile boolean running;
    private DatagramSocket socket;
    private InetAddress address;
    private int port;
    private Thread thread;
    private int sequence;
    private long lastTimestamp;
    private StatusListener listener;

    public RtpMidiClient() {
        int value = new Random().nextInt();
        ssrc = value == 0 ? 1 : value;
    }

    public synchronized boolean start(String host, int port, StatusListener listener) {
        stop();
        this.listener = listener;
        if (host == null || host.trim().isEmpty()) {
            report("Set a host IP first", true);
            return false;
        }
        if (port <= 0 || port > 65535) {
            report("Invalid RTP-MIDI port", true);
            return false;
        }
        try {
            this.address = InetAddress.getByName(host.trim());
            this.port = port;
            this.socket = new DatagramSocket();
            this.running = true;
            this.thread = new Thread(this::run, "volna-rtp-midi");
            this.thread.start();
            report("RTP-MIDI -> " + host.trim() + ":" + port, false);
            return true;
        } catch (Exception e) {
            report(e.getMessage() == null ? "RTP-MIDI could not start" : e.getMessage(), true);
            stop();
            return false;
        }
    }

    public synchronized void stop() {
        running = false;
        DatagramSocket oldSocket = socket;
        socket = null;
        if (oldSocket != null) {
            oldSocket.close();
        }
        Thread oldThread = thread;
        thread = null;
        if (oldThread != null && oldThread != Thread.currentThread()) {
            try {
                oldThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        queue.clear();
    }

    public boolean isRunning() {
        return running;
    }

    /** Queue a raw MIDI 1.0 message (1-3 bytes). Safe to call from any thread. */
    public void send(byte[] data, int count) {
        if (!running || data == null || count <= 0) {
            return;
        }
        byte[] copy = Arrays.copyOf(data, Math.min(count, 3));
        if (!queue.offer(copy)) {
            queue.poll();
            queue.offer(copy);
        }
    }

    private void run() {
        List<byte[]> batch = new ArrayList<>(MAX_BATCH_MESSAGES);
        byte[] pending = null;
        while (running) {
            try {
                byte[] first = pending;
                pending = null;
                if (first == null) {
                    first = queue.poll(100, TimeUnit.MILLISECONDS);
                }
                if (first == null) {
                    continue;
                }

                batch.clear();
                batch.add(first);
                int total = 1 + first.length;
                while (batch.size() < MAX_BATCH_MESSAGES) {
                    byte[] next = queue.poll();
                    if (next == null) {
                        break;
                    }
                    int needed = 1 + next.length;
                    if (total + needed > MAX_MIDI_LIST) {
                        pending = next;
                        break;
                    }
                    batch.add(next);
                    total += needed;
                }

                byte[] packet = buildPacket(batch);
                DatagramSocket current = socket;
                if (current != null) {
                    current.send(new DatagramPacket(packet, packet.length, address, port));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                if (running) {
                    report(e.getMessage() == null ? "RTP-MIDI send failed" : e.getMessage(), true);
                }
                running = false;
                break;
            }
        }
    }

    private byte[] buildPacket(List<byte[]> messages) {
        int midiLength = 0;
        for (byte[] message : messages) {
            midiLength += 1 + message.length;
        }
        final boolean longHeader = midiLength > 15;
        final int headerLength = longHeader ? 2 : 1;
        final byte[] packet = new byte[12 + headerLength + midiLength];

        packet[0] = (byte) 0x80; // RTP v2
        packet[1] = (byte) 0x61; // M=1, payload type 97 (RTP-MIDI)
        int seq = sequence++ & 0xFFFF;
        packet[2] = (byte) (seq >>> 8);
        packet[3] = (byte) seq;

        long now = System.nanoTime();
        if (now <= lastTimestamp) {
            now = lastTimestamp + 1;
        }
        lastTimestamp = now;
        int timestamp = (int) ((now * CLOCK_RATE) / 1_000_000_000L);
        packet[4] = (byte) (timestamp >>> 24);
        packet[5] = (byte) (timestamp >>> 16);
        packet[6] = (byte) (timestamp >>> 8);
        packet[7] = (byte) timestamp;

        packet[8] = (byte) (ssrc >>> 24);
        packet[9] = (byte) (ssrc >>> 16);
        packet[10] = (byte) (ssrc >>> 8);
        packet[11] = (byte) ssrc;

        int pos = 12;
        if (longHeader) {
            // B=1, J=0, Z=1, P=0, LEN high nibble.
            packet[pos++] = (byte) (0x80 | 0x20 | ((midiLength >>> 8) & 0x0F));
            packet[pos++] = (byte) midiLength;
        } else {
            // B=0, J=0, Z=1, P=0, LEN low nibble.
            packet[pos++] = (byte) (0x20 | midiLength);
        }
        for (byte[] message : messages) {
            packet[pos++] = 0; // delta time 0
            System.arraycopy(message, 0, packet, pos, message.length);
            pos += message.length;
        }
        return packet;
    }

    private void report(String message, boolean error) {
        StatusListener current = listener;
        if (current != null) {
            current.onStatus(message, error);
        }
    }
}
