package com.example.sourcecompare.domain;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Represents an uploaded archive in a framework-agnostic way.
 */
public class ArchiveInput {
    private final String filename;
    private final InputStreamSupplier inputStreamSupplier;

    public ArchiveInput(String filename, InputStreamSupplier inputStreamSupplier) {
        this.filename = filename != null ? filename : "";
        this.inputStreamSupplier = Objects.requireNonNull(inputStreamSupplier, "inputStreamSupplier");
    }

    public String filename() {
        return filename;
    }

    public InputStream openStream() throws IOException {
        return inputStreamSupplier.openStream();
    }

    @FunctionalInterface
    public interface InputStreamSupplier {
        InputStream openStream() throws IOException;
    }
}
