package local.orange.z1androidtest;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Deterministic host-only stream and layout tests. Does not open a MIDI device. */
public final class ProtocolFuzzTest {
    private static final long SEED = 0x5A314D494449L;
    private static final Random RANDOM = new Random(SEED);
    private static int checks;

    private static void check(boolean condition, String detail) {
        checks++;
        if (!condition) throw new AssertionError(detail);
    }

    private static String event(int status, int... data) {
        return String.format("%02X", status) + ":" + Arrays.toString(data);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }

    private static String hex(byte[] input) {
        StringBuilder out = new StringBuilder();
        for (byte value : input) {
            if (out.length() != 0) out.append(' ');
            out.append(String.format("%02X", value & 0xff));
        }
        return out.toString();
    }

    private static void feed(PadProtocol.Parser parser, List<String> seen,
                             byte[] input, int start, int length) {
        // The extra bytes prove feed respects both offset and length, including empty slices.
        byte[] guarded = new byte[length + 2];
        guarded[0] = (byte) 0x90;
        guarded[guarded.length - 1] = 127;
        System.arraycopy(input, start, guarded, 1, length);
        parser.feed(guarded, 1, length, (status, data) -> seen.add(event(status, data)));
    }

    private static void assertEvents(String label, byte[] input, List<String> expected,
                                     List<String> actual, String partition) {
        check(expected.equals(actual), label + " [" + hex(input) + "] " + partition
                + " expected=" + expected + " actual=" + actual);
    }

    // Every two-way boundary, plus seeded multi-packet partitions, with independent expectations.
    private static void partitions(String label, byte[] input, List<String> expected) {
        for (int split = 0; split <= input.length; split++) {
            PadProtocol.Parser parser = new PadProtocol.Parser();
            List<String> actual = new ArrayList<>();
            feed(parser, actual, input, 0, split);
            feed(parser, actual, input, split, input.length - split);
            assertEvents(label, input, expected, actual, "split=" + split);
        }
        for (int trial = 0; trial < 8; trial++) {
            PadProtocol.Parser parser = new PadProtocol.Parser();
            List<String> actual = new ArrayList<>();
            for (int at = 0; at < input.length;) {
                int length = Math.min(input.length - at, 1 + RANDOM.nextInt(11));
                feed(parser, actual, input, at, length);
                at += length;
            }
            assertEvents(label, input, expected, actual, "random partition=" + trial);
        }
    }

    private static void layout() {
        Set<Integer> notes = new HashSet<>();
        Set<Integer> samples = new HashSet<>();
        for (int cell = 0; cell < 64; cell++) {
            int rowFromBottom = 7 - cell / 8;
            int column = cell % 8;
            int note = 36 + 4 * rowFromBottom + column % 4 + 32 * (column / 4);
            int sample = 8 * rowFromBottom + column;
            check(PadProtocol.drum(cell) == note, "drum cell=" + cell);
            check(PadProtocol.sample(cell) == sample, "sample cell=" + cell);
            check(PadProtocol.cell(note) == cell, "inverse note=" + note);
            notes.add(note);
            samples.add(sample);
        }
        check(notes.size() == 64 && samples.size() == 64, "layout bijections");
        for (int note = -128; note <= 255; note++) {
            if (note < 36 || note > 99) check(PadProtocol.cell(note) == -1, "out of range " + note);
        }
        check(PadProtocol.cell(Integer.MIN_VALUE) == -1 && PadProtocol.cell(Integer.MAX_VALUE) == -1,
                "extreme note ranges");
    }

    private static void channelMessages() {
        // All 16 channels and all seven channel message kinds, including one-byte Cx/Dx.
        for (int status = 0x80; status <= 0xef; status++) {
            boolean oneByte = (status & 0xf0) == 0xc0 || (status & 0xf0) == 0xd0;
            byte[] input = oneByte ? bytes(status, 0, 127, 64)
                    : bytes(status, 0, 127, 127, 0, 64, 1);
            List<String> expected = oneByte
                    ? Arrays.asList(event(status, 0), event(status, 127), event(status, 64))
                    : Arrays.asList(event(status, 0, 127), event(status, 127, 0), event(status, 64, 1));
            partitions("channel/running status", input, expected);
        }
        // Parser preserves raw Note Off and zero-velocity Note On; the app interprets releases.
        for (int channel : new int[]{0, 2}) {
            for (int note = 0; note < 128; note++) {
                byte[] input = bytes(0x90 + channel, note, 127, note, 0,
                                     0x80 + channel, note, 64);
                partitions("CH1/CH3 note on and releases", input,
                        Arrays.asList(event(0x90 + channel, note, 127),
                                      event(0x90 + channel, note, 0),
                                      event(0x80 + channel, note, 64)));
            }
        }
    }

    private static void easyAndRealtime() {
        for (int value = 0; value < 128; value++) {
            byte[] input = bytes(0xf0, value, 0xf7);
            partitions("private EASY " + value, input, Arrays.asList(event(0xf0, value)));
            int index = value % 64;
            int cell = PadProtocol.cell(36 + index);
            check(cell >= 0 && cell < 64 && PadProtocol.drum(cell) - 36 == index,
                    "EASY index " + value);
        }
        byte[][] streams = { bytes(0x90, 36, 100, 37, 0, 0x80, 36, 64),
                             bytes(0xf0, 0, 0xf7, 0xf0, 127, 0xf7) };
        List<List<String>> expected = Arrays.asList(
                Arrays.asList(event(0x90, 36, 100), event(0x90, 37, 0), event(0x80, 36, 64)),
                Arrays.asList(event(0xf0, 0), event(0xf0, 127)));
        for (int s = 0; s < streams.length; s++) {
            for (int realtime = 0xf8; realtime <= 0xff; realtime++) {
                for (int gap = 0; gap <= streams[s].length; gap++) {
                    byte[] withRealtime = new byte[streams[s].length + 1];
                    System.arraycopy(streams[s], 0, withRealtime, 0, gap);
                    withRealtime[gap] = (byte) realtime;
                    System.arraycopy(streams[s], gap, withRealtime, gap + 1, streams[s].length - gap);
                    partitions("realtime in stream", withRealtime, expected.get(s));
                }
            }
        }
    }

    private static void systemCommonAndMalformed() {
        // System common cancels running status; its data and trailing orphan data cannot become notes.
        for (int status = 0xf1; status <= 0xf7; status++) {
            byte[] input = bytes(0x90, 36, 100, 37, status, 38, 127, 39, 100,
                                 0x92, 40, 1);
            partitions("system common abort " + status, input,
                    Arrays.asList(event(0x90, 36, 100), event(0x92, 40, 1)));
        }
        partitions("empty and orphan EOX", bytes(0xf7, 0xf0, 0xf7, 0x90, 36, 1),
                Arrays.asList(event(0x90, 36, 1)));
        partitions("nested SysEx restarts", bytes(0xf0, 1, 0xf0, 2, 0xf7),
                Arrays.asList(event(0xf0, 2)));
        // The generic parser forwards short non-EASY payloads; the app filters them by length.
        partitions("non-EASY SysEx", bytes(0xf0, 1, 2, 0xf7, 0x90, 40, 1),
                Arrays.asList(event(0xf0, 1, 2), event(0x90, 40, 1)));
        partitions("channel aborts SysEx", bytes(0xf0, 1, 0x90, 40, 100),
                Arrays.asList(event(0x90, 40, 100)));
        partitions("system common aborts SysEx", bytes(0xf0, 1, 0xf2, 2, 3, 0xf7, 0x90, 40, 100),
                Arrays.asList(event(0x90, 40, 100)));

        // Baseline uses a 512-data-byte buffer; overflow must not emit a partial SysEx.
        for (int size : new int[]{511, 512, 513, 16384}) {
            byte[] input = new byte[size + 6];
            input[0] = (byte) 0xf0;
            Arrays.fill(input, 1, size + 1, (byte) 42);
            input[size + 1] = (byte) 0xf7;
            input[size + 2] = (byte) 0x90;
            input[size + 3] = 36;
            input[size + 4] = 1;
            input[size + 5] = (byte) 0xf8;
            int[] data = new int[size];
            Arrays.fill(data, 42);
            List<String> expected = new ArrayList<>();
            if (size <= 512) expected.add(event(0xf0, data));
            expected.add(event(0x90, 36, 1));
            if (size <= 513) {
                partitions("SysEx size " + size, input, expected);
            } else {
                // Do not generate quadratic test work for the stress case.
                PadProtocol.Parser parser = new PadProtocol.Parser();
                List<String> actual = new ArrayList<>();
                for (int at = 0; at < input.length;) {
                    int length = Math.min(137, input.length - at);
                    feed(parser, actual, input, at, length);
                    at += length;
                }
                assertEvents("large SysEx", input, expected, actual, "137-byte partitions");
            }
        }
        byte[] overflowAbort = new byte[520];
        overflowAbort[0] = (byte) 0xf0;
        Arrays.fill(overflowAbort, 1, 515, (byte) 1);
        overflowAbort[515] = (byte) 0x92;
        overflowAbort[516] = 36;
        overflowAbort[517] = 100;
        overflowAbort[518] = (byte) 0xf0;
        overflowAbort[519] = 2; // New message remains incomplete.
        partitions("overflow interrupted by channel", overflowAbort, Arrays.asList(event(0x92, 36, 100)));
        overflowAbort[515] = (byte) 0xf0;
        overflowAbort[516] = 3;
        overflowAbort[517] = (byte) 0xf7;
        overflowAbort[518] = (byte) 0x90;
        overflowAbort[519] = 36;
        partitions("overflow interrupted by new SysEx", overflowAbort, Arrays.asList(event(0xf0, 3)));
    }

    private static void resetAndIsolation() {
        PadProtocol.Parser first = new PadProtocol.Parser();
        PadProtocol.Parser second = new PadProtocol.Parser();
        List<String> a = new ArrayList<>(), b = new ArrayList<>();
        feed(first, a, bytes(0x90, 36), 0, 2);
        feed(second, b, bytes(0x92, 37), 0, 2);
        feed(first, a, bytes(100), 0, 1);
        feed(second, b, bytes(1), 0, 1);
        check(a.equals(Arrays.asList(event(0x90, 36, 100))), "first parser state");
        check(b.equals(Arrays.asList(event(0x92, 37, 1))), "second parser state");
        first.reset(); // A device replacement must not retain running status or partial messages.
        feed(first, a, bytes(38, 127), 0, 2);
        check(a.size() == 1, "reset cancels running status");
        feed(second, b, bytes(38, 2), 0, 2);
        check(b.equals(Arrays.asList(event(0x92, 37, 1), event(0x92, 38, 2))),
                "reset of first parser must not alter second");
        feed(first, a, bytes(0x90, 39), 0, 2);
        first.reset();
        feed(first, a, bytes(3), 0, 1);
        check(a.size() == 1, "reset cancels incomplete channel message");
        feed(first, a, bytes(0xf0, 5), 0, 2);
        first.reset();
        feed(first, a, bytes(0xf7, 0xf0, 6, 0xf7), 0, 4);
        check(a.equals(Arrays.asList(event(0x90, 36, 100), event(0xf0, 6))),
                "reset cancels incomplete SysEx and recovers");
        byte[] oversizedPartial = new byte[514];
        oversizedPartial[0] = (byte) 0xf0;
        Arrays.fill(oversizedPartial, 1, oversizedPartial.length, (byte) 1);
        feed(first, a, oversizedPartial, 0, oversizedPartial.length);
        first.reset();
        feed(first, a, bytes(0xf0, 7, 0xf7), 0, 3);
        check(a.equals(Arrays.asList(event(0x90, 36, 100), event(0xf0, 6), event(0xf0, 7))),
                "reset clears overflow state");
        feed(first, a, bytes(0xf0, 8), 0, 2);
        feed(second, b, bytes(0xf0, 9), 0, 2);
        feed(second, b, bytes(0xf7), 0, 1);
        feed(first, a, bytes(0xf7), 0, 1);
        check(a.get(a.size() - 1).equals(event(0xf0, 8)) &&
              b.get(b.size() - 1).equals(event(0xf0, 9)), "independent SysEx state");
    }

    private static void generatedStreams() {
        // Generate grammar and expected events together, rather than comparing to another parser.
        for (int trial = 0; trial < 50; trial++) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            List<String> expected = new ArrayList<>();
            for (int message = 0; message < 30; message++) {
                int kind = RANDOM.nextInt(3);
                if (kind == 0) {
                    int status = 0x80 + RANDOM.nextInt(0x70);
                    int count = ((status & 0xf0) == 0xc0 || (status & 0xf0) == 0xd0) ? 1 : 2;
                    stream.write(status);
                    for (int repetition = 0; repetition < 1 + message % 3; repetition++) {
                        int[] data = new int[count];
                        for (int d = 0; d < count; d++) {
                            if (RANDOM.nextBoolean()) stream.write(0xf8 + RANDOM.nextInt(8));
                            data[d] = RANDOM.nextInt(128);
                            stream.write(data[d]);
                        }
                        expected.add(event(status, data));
                    }
                } else if (kind == 1) {
                    int value = RANDOM.nextInt(128);
                    stream.write(0xf0);
                    stream.write(value);
                    if (RANDOM.nextBoolean()) stream.write(0xfe);
                    stream.write(0xf7);
                    expected.add(event(0xf0, value));
                } else {
                    stream.write(0xf1 + RANDOM.nextInt(6));
                    stream.write(RANDOM.nextInt(128));
                    stream.write(RANDOM.nextInt(128));
                }
            }
            partitions("generated stream " + trial, stream.toByteArray(), expected);
        }
    }

    public static void main(String[] args) {
        layout();
        channelMessages();
        easyAndRealtime();
        systemCommonAndMalformed();
        resetAndIsolation();
        generatedStreams();
        System.out.println("PASS: ProtocolFuzzTest seed=" + SEED + " checks=" + checks);
    }
}
