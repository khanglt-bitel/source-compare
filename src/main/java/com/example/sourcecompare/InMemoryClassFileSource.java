package com.example.sourcecompare;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.util.getopt.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class InMemoryClassFileSource implements ClassFileSource {
    private final ConcurrentMap<String, byte[]> classes = new ConcurrentHashMap<>();

    String register(byte[] bytecode) {
        String path = "memory/" + UUID.randomUUID() + ".class";
        classes.put(path, bytecode);
        return path;
    }

    void release(String path) {
        classes.remove(path);
    }

    @Override
    public void informAnalysisRelativePathDetail(String usePath, String classFilePath) {}

    @Override
    public void informAnalysisPath(String path, String jarClassPath) {}

    @Override
    public Collection<String> addJar(String jarPath) {
        return Collections.emptyList();
    }

    @Override
    public String getPossiblyRenamedPath(String path) {
        return path;
    }

    @Override
    public Pair<byte[], String> getClassFileContent(String path) throws IOException {
        byte[] bytecode = classes.get(path);
        if (bytecode == null) {
            bytecode = loadFromClassLoader(path);
        }
        if (bytecode == null) {
            throw new IOException("Class bytes not found for path: " + path);
        }
        return Pair.make(bytecode, path);
    }

    @Override
    public Map<String, String> getManifestEntries() {
        return Collections.emptyMap();
    }

    @Override
    public void close() {
        classes.clear();
    }

    private byte[] loadFromClassLoader(String path) throws IOException {
        String resourcePath = path;
        int moduleSeparator = resourcePath.indexOf(':');
        if (moduleSeparator >= 0) {
            resourcePath = resourcePath.substring(moduleSeparator + 1);
        }
        int jarSeparator = resourcePath.indexOf('!');
        if (jarSeparator >= 0) {
            resourcePath = resourcePath.substring(jarSeparator + 1);
        }
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }
        if (!resourcePath.endsWith(".class")) {
            resourcePath = resourcePath + ".class";
        }
        resourcePath = resourcePath.replace('\\', '/');
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }
            return stream.readAllBytes();
        }
    }
}
