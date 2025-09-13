package com.example.sourcecompare;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DecompileService {
    public Map<String, FileInfo> decompileClasses(MultipartFile zip) throws IOException {
        Map<String, FileInfo> result = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    byte[] bytes = zis.readAllBytes();
                    String name = entry.getName();
                    result.put(name, new FileInfo(name, decompile(bytes)));
                }
            }
        }
        return result;
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
