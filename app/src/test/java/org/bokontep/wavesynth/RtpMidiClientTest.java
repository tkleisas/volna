package org.bokontep.wavesynth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class RtpMidiClientTest {
    @Test
    public void rejectsBlankHostAndInvalidPort() {
        AtomicReference<String> error = new AtomicReference<>();
        RtpMidiClient client = new RtpMidiClient();
        assertFalse(client.start("", 5004,
                (message, isError) -> error.set(message + ":" + isError)));
        assertTrue(error.get().contains("host"));

        error.set(null);
        assertFalse(client.start("127.0.0.1", 0,
                (message, isError) -> error.set(message + ":" + isError)));
        assertTrue(error.get().contains("port"));
        assertFalse(client.isRunning());
    }

    @Test
    public void sendBeforeStartAndRepeatedStopAreSafe() {
        RtpMidiClient client = new RtpMidiClient();
        client.send(new byte[] {(byte) 0x90, 60, 100}, 3);
        client.stop();
        client.stop();
        assertFalse(client.isRunning());
    }

    @Test
    public void sendsRtpMidiPacket() throws Exception {
        try (DatagramSocket receiver = new DatagramSocket(0)) {
            receiver.setSoTimeout(3000);
            final int port = receiver.getLocalPort();
            RtpMidiClient client = new RtpMidiClient();
            assertTrue(client.start("127.0.0.1", port,
                    (message, error) -> { }));

            client.send(new byte[] {(byte) 0x90, 60, 100}, 3);
            client.send(new byte[] {(byte) 0xB0, 74, 42}, 3);

            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiver.receive(packet);
            byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
            client.stop();

            assertEquals(12 + 1 + 8, data.length);
            assertEquals((byte) 0x80, data[0]);
            assertEquals((byte) 0x61, data[1]);
            assertEquals((byte) 0x28, data[12]); // Z=1, LEN=8
            assertEquals(0, data[13]);
            assertEquals((byte) 0x90, data[14]);
            assertEquals(60, data[15]);
            assertEquals(100, data[16]);
            assertEquals(0, data[17]);
            assertEquals((byte) 0xB0, data[18]);
            assertEquals(74, data[19]);
            assertEquals(42, data[20]);
        }
    }
}
