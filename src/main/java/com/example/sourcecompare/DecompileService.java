package com.example.sourcecompare;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.stream.Stream;

@Service
public class DecompileService {
    private static final Object FERNFLOWER_LOCK = new Object();
    private static final String CONSOLE_DECOMPILER_CLASS =
            "org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler";
    private final int decompileThreadPoolSize;

    public DecompileService(@Value("${decompile.thread-pool-size:0}") int decompileThreadPoolSize) {
        int defaultPoolSize = Runtime.getRuntime().availableProcessors();
        int configuredPoolSize = decompileThreadPoolSize > 0 ? decompileThreadPoolSize : defaultPoolSize;
        this.decompileThreadPoolSize = Math.max(1, configuredPoolSize);
    }

    public Map<String, FileInfo> decompileClasses(MultipartFile zip) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(decompileThreadPoolSize);
        CompletionService<Map.Entry<String, FileInfo>> completionService =
                new ExecutorCompletionService<>(executor);

        List<String> entryOrder = new ArrayList<>();
        int submittedTasks = 0;

        try {
            try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                        continue;
                    }

                    String entryName = entry.getName();
                    entryOrder.add(entryName);
                    byte[] classBytes = zis.readAllBytes();
                    zis.closeEntry();

                    completionService.submit(
                            () -> {
                                try {
                                    FileInfo info =
                                            new FileInfo(entryName, decompile(entryName, classBytes));
                                    return Map.entry(entryName, info);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                    submittedTasks++;
                }
            }

            if (submittedTasks == 0) {
                return new HashMap<>();
            }

            Map<String, FileInfo> unorderedResults = new HashMap<>();
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
        } finally {
            executor.shutdownNow();
        }
    }

    public String decompile(String entryName, byte[] classBytes) throws IOException {
        Path tempRoot = Files.createTempDirectory("fernflower");
        Path inputDir = tempRoot.resolve("input");
        Path outputDir = tempRoot.resolve("output");
        try {
            Path relativeClassPath;
            try {
                relativeClassPath = Path.of(entryName).normalize();
            } catch (InvalidPathException e) {
                throw new IOException("Invalid class entry path: " + entryName, e);
            }
            if (relativeClassPath.getNameCount() == 0
                    || relativeClassPath.isAbsolute()
                    || relativeClassPath.startsWith("..")) {
                throw new IOException("Invalid class entry path: " + entryName);
            }
            Path classPath = inputDir.resolve(relativeClassPath);
            Path parent = classPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            } else {
                Files.createDirectories(inputDir);
            }
            Files.write(classPath, classBytes);

            Files.createDirectories(outputDir);

            List<String> args = new ArrayList<>();
            args.add(inputDir.toAbsolutePath().toString());
            args.add(outputDir.toAbsolutePath().toString());

            invokeFernflower(args);

            String javaRelative = relativeClassPath.toString().replaceAll("\\.class$", ".java");
            Path javaPath = outputDir.resolve(javaRelative);
            if (!Files.exists(javaPath)) {
                throw new IOException("FernFlower did not produce output for " + entryName);
            }
            return Files.readString(javaPath, StandardCharsets.UTF_8);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to invoke FernFlower decompiler", e);
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private void invokeFernflower(List<String> args) throws ReflectiveOperationException {
        synchronized (FERNFLOWER_LOCK) {
            Class<?> consoleClass = Class.forName(CONSOLE_DECOMPILER_CLASS);
            Method mainMethod = consoleClass.getMethod("main", String[].class);
            try {
                mainMethod.invoke(null, (Object) args.toArray(String[]::new));
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new ReflectiveOperationException("FernFlower invocation failed", cause);
            }
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                    // best-effort cleanup
                                }
                            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
