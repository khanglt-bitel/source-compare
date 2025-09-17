package com.example.sourcecompare.web;

import com.example.sourcecompare.domain.ArchiveInput;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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
        String sanitizedOriginal = sanitizeFilename(originalFilename);
        String sanitizedFallback = sanitizeFilename(file.getName());

        String safeFilename = !sanitizedOriginal.isEmpty() ? sanitizedOriginal : sanitizedFallback;
        String extensionSource =
                firstNonBlank(
                        safeFilename,
                        sanitizedOriginal,
                        sanitizedFallback,
                        originalFilename,
                        file.getName());
        String lowerCaseName = extensionSource.toLowerCase(Locale.ROOT);

        if (lowerCaseName.endsWith(".zip") || lowerCaseName.endsWith(".jar")) {
            return new ArchiveInput(safeFilename, file::getInputStream);
        }

        if (lowerCaseName.endsWith(".class") || lowerCaseName.endsWith(".java")) {
            try {
                byte[] content = file.getBytes();
                String entryName = singleEntryName(safeFilename, extensionSource);
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

    private static String singleEntryName(String sanitizedFilename, String extensionSource) {
        if (!sanitizedFilename.isBlank()) {
            return sanitizedFilename;
        }

        String extension = extractExtension(extensionSource);
        return "file" + extension;
    }

    private static String sanitizeFilename(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return "";
        }

        String cleaned = StringUtils.cleanPath(candidate.trim());
        if (!StringUtils.hasText(cleaned)) {
            return "";
        }

        int lastSlash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        String withoutDirectories = cleaned.substring(lastSlash + 1);
        return withoutDirectories.trim();
    }

    private static String extractExtension(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }

        int extensionIndex = name.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == name.length() - 1) {
            return "";
        }

        return name.substring(extensionIndex);
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return "";
    }
}
