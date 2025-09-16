package com.example.sourcecompare;

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DecompileService {
    private final int decompileThreadPoolSize;
    private final ExecutorService executor;

    public DecompileService(@Value("${decompile.thread-pool-size:0}") int decompileThreadPoolSize) {
        int defaultPoolSize = Runtime.getRuntime().availableProcessors();
        int configuredPoolSize = decompileThreadPoolSize > 0 ? decompileThreadPoolSize : defaultPoolSize;
        this.decompileThreadPoolSize = Math.max(1, configuredPoolSize);
        this.executor = Executors.newFixedThreadPool(decompileThreadPoolSize);
    }

    public Map<String, FileInfo> decompileClasses(MultipartFile zip) throws IOException {
        CompletionService<Map.Entry<String, FileInfo>> completionService =
                new ExecutorCompletionService<>(executor);

        List<String> entryOrder = new ArrayList<>();
        Map<String, FileInfo> unorderedResults = new HashMap<>();
        int submittedTasks = 0;

        try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                entryOrder.add(entryName);
                byte[] entryBytes = zis.readAllBytes();
                zis.closeEntry();

                if (entryName.endsWith(".class")) {
                    byte[] classBytes = entryBytes;
                    completionService.submit(
                            () -> {
                                try {
                                    FileInfo info = new FileInfo(entryName, decompile(classBytes));
                                    return Map.entry(entryName, info);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                    submittedTasks++;
                } else {
                    unorderedResults.put(
                            entryName, new FileInfo(entryName, new String(entryBytes, StandardCharsets.UTF_8)));
                }
            }
        }

        for (int i = 0; i < submittedTasks; i++) {
            try {
                Future<Map.Entry<String, FileInfo>> future = completionService.take();
                Map.Entry<String, FileInfo> entry = future.get();
                unorderedResults.put(entry.getKey(), entry.getValue());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Decompilation interrupted", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof UncheckedIOException) {
                    throw ((UncheckedIOException) cause).getCause();
                }
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new IOException("Failed to decompile class", cause);
            }
        }

        Map<String, FileInfo> orderedResult = new LinkedHashMap<>();
        for (String name : entryOrder) {
            FileInfo info = unorderedResults.get(name);
            if (info != null) {
                orderedResult.put(name, info);
            }
        }
        return orderedResult;
    }

    public String decompile(byte[] classBytes) throws IOException {
        Path inputDir = Files.createTempDirectory("quiltflower-input");
        Path outputDir = Files.createTempDirectory("quiltflower-output");
        Path classFile = inputDir.resolve("Temp.class");
        Files.write(classFile, classBytes);

        Map<String, Object> options = new HashMap<>();
        options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
        options.put(IFernflowerPreferences.REMOVE_BRIDGE, "1");

        IFernflowerLogger logger =
                new IFernflowerLogger() {
                    @Override
                    public void writeMessage(String message, Severity severity) {}

                    @Override
                    public void writeMessage(String message, Severity severity, Throwable t) {}
                };

        ConsoleDecompiler decompiler = new ConsoleDecompilerPublic(outputDir.toFile(), options, logger);

        try {
            decompiler.addSource(classFile.toFile());
            decompiler.decompileContext();

            Path javaFile = findDecompiledFile(outputDir);
            return Files.readString(javaFile);
        } catch (RuntimeException e) {
            throw new IOException("Failed to decompile class with Quiltflower", e);
        } finally {
            deleteRecursively(inputDir);
            deleteRecursively(outputDir);
        }
    }

    private Path findDecompiledFile(Path outputDir) throws IOException {
        try (Stream<Path> stream = Files.walk(outputDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new IOException(
                                            "Quiltflower did not produce any .java output for class"));
        }
    }

    private void deleteRecursively(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException ignored) {
            // Best-effort cleanup
        }
    }
}
