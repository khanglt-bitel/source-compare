package com.example.sourcecompare.web;

import com.example.sourcecompare.application.ArchiveDecompiler;
import com.example.sourcecompare.application.ComparisonUseCase;
import com.example.sourcecompare.application.DiffRenderer;
import com.example.sourcecompare.application.JavaSourceNormalizer;
import com.example.sourcecompare.application.SourceFormatter;
import com.example.sourcecompare.domain.ArchiveInput;
import com.example.sourcecompare.domain.ComparisonMode;
import com.example.sourcecompare.domain.ComparisonRequest;
import com.example.sourcecompare.domain.ComparisonResult;
import com.example.sourcecompare.domain.FileInfo;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MultipartArchiveInputAdapterTests {
    private final MultipartArchiveInputAdapter adapter = new MultipartArchiveInputAdapter();

    private final JavaSourceNormalizer normalizer = source -> source;
    private final SourceFormatter formatter = FileInfo::new;
    private final DiffRenderer diffRenderer = (fileName, original, revised, contextSize, unreadPlaceholder) -> "diff";

    @Test
    void classUploadIsWrappedIntoArchive() throws IOException {
        byte[] leftBytes = new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        byte[] rightBytes = new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBF};

        ArchiveInput left =
                adapter.adapt(
                        new MockMultipartFile(
                                "left", "Example.class", "application/java-vm", leftBytes));
        ArchiveInput right =
                adapter.adapt(
                        new MockMultipartFile(
                                "right", "Example.class", "application/java-vm", rightBytes));

        ComparisonUseCase useCase =
                new ComparisonUseCase(new RecordingArchiveDecompiler(), normalizer, formatter, diffRenderer);
        ComparisonRequest request = new ComparisonRequest(left, right, ComparisonMode.CLASS_VS_CLASS, 1, false);

        ComparisonResult result = useCase.compare(request);
        assertNotNull(result);
    }

    @Test
    void javaUploadIsWrappedIntoArchive() throws IOException {
        ArchiveInput left =
                adapter.adapt(
                        new MockMultipartFile(
                                "left",
                                "Sample.java",
                                "text/plain",
                                "class Left {}".getBytes(StandardCharsets.UTF_8)));
        ArchiveInput right =
                adapter.adapt(
                        new MockMultipartFile(
                                "right",
                                "Sample.java",
                                "text/plain",
                                "class Right {}".getBytes(StandardCharsets.UTF_8)));

        ComparisonUseCase useCase =
                new ComparisonUseCase(new RecordingArchiveDecompiler(), normalizer, formatter, diffRenderer);
        ComparisonRequest request = new ComparisonRequest(left, right, ComparisonMode.SOURCE_VS_SOURCE, 0, false);

        ComparisonResult result = useCase.compare(request);
        assertNotNull(result);
    }

    private static final class RecordingArchiveDecompiler implements ArchiveDecompiler {
        @Override
        public Map<String, FileInfo> decompileClasses(ArchiveInput archive) throws IOException {
            Map<String, FileInfo> result = new LinkedHashMap<>();
            try (ZipInputStream zis = new ZipInputStream(archive.openStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                        byte[] bytes = zis.readAllBytes();
                        result.put(entry.getName(), new FileInfo(entry.getName(), "len:" + bytes.length));
                    }
                }
            }
            return result;
        }
    }
}

