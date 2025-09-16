package com.example.sourcecompare;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecompileServiceTest {

    @Test
    void decompileClassesPreservesZipOrder() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("pkg/Foo.class", new byte[] {(byte) 30});
        entries.put("pkg/readme.txt", "resource".getBytes(StandardCharsets.UTF_8));
        entries.put("pkg/archive.tar", new byte[] {1, 2, 3});
        entries.put("pkg/picture.jpg", new byte[] {4});
        entries.put("pkg/docs/report.pdf", new byte[] {5, 6});
        entries.put("pkg/Bar.class", new byte[] {(byte) 5});
        entries.put("pkg/Baz.class", new byte[] {(byte) 10});

        TrackingDecompileService service =
                new TrackingDecompileService(
                        2,
                        bytes -> {
                            int delay = Byte.toUnsignedInt(bytes[0]);
                            try {
                                TimeUnit.MILLISECONDS.sleep(delay);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IOException("Interrupted while decompiling", e);
                            }
                            return "delay-" + delay;
                        });

        MockMultipartFile archive = zipFromEntries(entries);
        Map<String, FileInfo> result = service.decompileClasses(archive);

        assertEquals(entries.size(), result.size());
        assertIterableEquals(entries.keySet(), result.keySet(), "Decompiled files should follow ZIP order");
        Set<String> placeholderEntries =
                Set.of("pkg/archive.tar", "pkg/picture.jpg", "pkg/docs/report.pdf");

        result.forEach(
                (name, info) -> {
                    assertEquals(name, info.getName());
                    byte[] originalBytes = entries.get(name);
                    if (name.endsWith(".class")) {
                        int delay = Byte.toUnsignedInt(originalBytes[0]);
                        assertEquals("delay-" + delay, info.getContent());
                    } else if (placeholderEntries.contains(name)) {
                        assertEquals("CONTENT_NOT_READ", info.getContent());
                    } else {
                        assertEquals(new String(originalBytes, StandardCharsets.UTF_8), info.getContent());
                    }
                });
    }

    @Test
    void largeArchiveStreamingLimitsMemoryUsage() throws IOException {
        int entryCount = 200;
        int entrySize = 256 * 1024; // 256 KB per entry (~50 MB uncompressed)
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < entryCount; i++) {
            entries.put(
                    String.format("pkg/Class%03d.class", i),
                    entryBytes(entrySize, (byte) (i & 0x7F)));
        }

        byte[] zipBytes = createZipBytes(entries);
        MockMultipartFile archive =
                new MockMultipartFile("file", "classes.zip", "application/zip", zipBytes);

        // Allow the large helper structures to be reclaimed before measuring usage.
        entries = null;
        zipBytes = null;

        TrackingDecompileService service =
                new TrackingDecompileService(4, bytes -> "size-" + bytes.length);

        long before = usedMemory();
        Map<String, FileInfo> result = service.decompileClasses(archive);
        assertEquals(entryCount, result.size());

        archive = null;
        result = null;

        long after = usedMemory();
        long delta = after - before;
        assertTrue(
                delta < 64L * 1024 * 1024,
                () -> "Streaming decompilation should not retain all class bytes. Memory delta was " + delta);

        long expectedPeak = (long) entrySize * 4;
        assertTrue(
                service.getPeakActiveBytes() <= expectedPeak,
                () ->
                        "Active class byte peak should stay within pool size bounds. Expected <= "
                                + expectedPeak
                                + ", but was "
                                + service.getPeakActiveBytes());
    }

    private static MockMultipartFile zipFromEntries(Map<String, byte[]> entries) throws IOException {
        return new MockMultipartFile("file", "classes.zip", "application/zip", createZipBytes(entries));
    }

    private static byte[] createZipBytes(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private static byte[] entryBytes(int size, byte value) {
        byte[] data = new byte[size];
        Arrays.fill(data, value);
        return data;
    }

    private static long usedMemory() {
        System.gc();
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    @FunctionalInterface
    private interface DecompileFn {
        String apply(byte[] bytes) throws IOException;
    }

    private static final class TrackingDecompileService extends DecompileService {
        private final DecompileFn delegate;
        private final AtomicLong activeBytes = new AtomicLong();
        private final AtomicLong peakBytes = new AtomicLong();

        private TrackingDecompileService(int poolSize, DecompileFn delegate) {
            super(poolSize);
            this.delegate = delegate;
        }

        @Override
        public String decompile(byte[] classBytes) throws IOException {
            long current = activeBytes.addAndGet(classBytes.length);
            peakBytes.updateAndGet(prev -> Math.max(prev, current));
            try {
                return delegate.apply(classBytes);
            } finally {
                activeBytes.addAndGet(-classBytes.length);
            }
        }

        private long getPeakActiveBytes() {
            return peakBytes.get();
        }
    }
}
