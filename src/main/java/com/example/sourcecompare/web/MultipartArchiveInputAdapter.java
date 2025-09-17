package com.example.sourcecompare.web;

import com.example.sourcecompare.domain.ArchiveInput;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class MultipartArchiveInputAdapter {
    public ArchiveInput adapt(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String safeFilename = originalFilename != null ? originalFilename : "";
        String lowerCaseName = safeFilename.toLowerCase(Locale.ROOT);

        if (lowerCaseName.endsWith(".zip") || lowerCaseName.endsWith(".jar")) {
            return new ArchiveInput(safeFilename, file::getInputStream);
        }

        if (lowerCaseName.endsWith(".class") || lowerCaseName.endsWith(".java")) {
            try {
                byte[] content = file.getBytes();
                String entryName = singleEntryName(originalFilename, lowerCaseName);
                byte[] archiveBytes = createSingleEntryArchive(entryName, content);
                return new ArchiveInput(safeFilename, () -> new ByteArrayInputStream(archiveBytes));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to buffer uploaded file", e);
            }
        }

        return new ArchiveInput(safeFilename, file::getInputStream);
    }

    private static byte[] createSingleEntryArchive(String entryName, byte[] content) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                zos.write(content);
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create in-memory archive", e);
        }
    }

    private static String singleEntryName(String originalFilename, String lowerCaseName) {
        if (originalFilename != null) {
            String trimmed = originalFilename.trim();
            if (!trimmed.isEmpty()) {
                int lastSlash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
                String candidate = trimmed.substring(lastSlash + 1);
                if (!candidate.isBlank()) {
                    return candidate;
                }
            }
        }

        int extensionIndex = lowerCaseName.lastIndexOf('.');
        String extension = extensionIndex >= 0 ? lowerCaseName.substring(extensionIndex) : "";
        return "file" + extension;
    }
}
