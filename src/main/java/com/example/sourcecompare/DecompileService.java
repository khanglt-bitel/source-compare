package com.example.sourcecompare;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DecompileService {
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
                                    FileInfo info = new FileInfo(entryName, decompile(classBytes));
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

    public String decompile(byte[] classBytes) throws IOException {
        Path tempClass = Files.createTempFile("cfr", ".class");
        Files.write(tempClass, classBytes);
        StringBuilder out = new StringBuilder();
        OutputSinkFactory sink =
                new OutputSinkFactory() {
                    @Override
                    public List<SinkClass> getSupportedSinks(
                            SinkType sinkType, Collection<SinkClass> collection) {
                        return List.of(SinkClass.STRING);
                    }

                    @Override
                    public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                        return t -> out.append(t).append(System.lineSeparator());
                    }
                };
        CfrDriver driver = new CfrDriver.Builder().withOutputSink(sink).build();
        driver.analyse(Collections.singletonList(tempClass.toString()));
        Files.deleteIfExists(tempClass);
        // Somehow it return with "Analysis by" => remove
        String result = "//" + out.toString();
        return result;
    }
}
