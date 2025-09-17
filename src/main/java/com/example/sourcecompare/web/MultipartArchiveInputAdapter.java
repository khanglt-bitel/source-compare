package com.example.sourcecompare.web;

import com.example.sourcecompare.domain.ArchiveInput;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
public class MultipartArchiveInputAdapter {
    public ArchiveInput adapt(MultipartFile file) {
        return new ArchiveInput(file.getOriginalFilename(), file::getInputStream);
    }

    public ArchiveInput adapt(MultipartFile[] files) throws IOException {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("At least one file must be provided");
        }
        if (files.length == 1) {
            return adapt(files[0]);
        }
        List<MultipartFile> fileList = new ArrayList<>(files.length);
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                fileList.add(file);
            }
        }
        if (fileList.isEmpty()) {
            throw new IllegalArgumentException("At least one non-empty file must be provided");
        }
        if (fileList.size() == 1) {
            return adapt(fileList.get(0));
        }
        return adaptMultiple(fileList);
    }

    public String describeFilenames(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return "0 files";
        }
        List<String> names = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null) {
                continue;
            }
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null && !originalFilename.isBlank()) {
                names.add(originalFilename);
            }
        }
        if (names.isEmpty()) {
            return files.length == 1 ? "1 file" : files.length + " files";
        }
        return String.join(", ", names);
    }

    private ArchiveInput adaptMultiple(List<MultipartFile> files) throws IOException {
        Path tempFile = Files.createTempFile("combined-archive", ".zip");
        tempFile.toFile().deleteOnExit();
        try {
            writeCombinedArchive(tempFile, files);
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
        return new ArchiveInput(buildCombinedName(files), () -> Files.newInputStream(tempFile));
    }

    private void writeCombinedArchive(Path destination, List<MultipartFile> files) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destination))) {
            byte[] buffer = new byte[8192];
            Set<String> usedPrefixes = new HashSet<>();
            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                String prefix = ensureUniquePrefix(derivePrefix(file, index), usedPrefixes);
                try (InputStream inputStream = file.getInputStream();
                        ZipInputStream zis = new ZipInputStream(inputStream)) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.isDirectory()) {
                            continue;
                        }
                        String entryName = sanitizeEntryName(entry.getName());
                        if (entryName.isEmpty()) {
                            continue;
                        }
                        ZipEntry newEntry = new ZipEntry(prefix + "/" + entryName);
                        zos.putNextEntry(newEntry);
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            zos.write(buffer, 0, read);
                        }
                        zos.closeEntry();
                    }
                }
            }
        }
    }

    private String buildCombinedName(List<MultipartFile> files) {
        String combined =
                files.stream()
                        .map(MultipartFile::getOriginalFilename)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(name -> stripExtension(name).replaceAll("[^A-Za-z0-9._-]", "_"))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining("-"));
        if (combined.isEmpty()) {
            combined = "combined";
        }
        return combined + ".zip";
    }

    private String derivePrefix(MultipartFile file, int index) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return "archive-" + (index + 1);
        }
        String sanitized = stripExtension(originalFilename).replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isBlank()) {
            sanitized = "archive-" + (index + 1);
        }
        return sanitized;
    }

    private String ensureUniquePrefix(String prefix, Set<String> usedPrefixes) {
        String candidate = prefix;
        int counter = 1;
        while (!usedPrefixes.add(candidate)) {
            candidate = prefix + "-" + counter++;
        }
        return candidate;
    }

    private String stripExtension(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String simple = slash >= 0 ? name.substring(slash + 1) : name;
        int dotIndex = simple.lastIndexOf('.');
        if (dotIndex <= 0) {
            return simple;
        }
        return simple.substring(0, dotIndex);
    }

    private String sanitizeEntryName(String entryName) {
        String normalized = entryName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        // Prevent directory traversal attempts
        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.equals("..") || segment.isEmpty()) {
                continue;
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }
}
