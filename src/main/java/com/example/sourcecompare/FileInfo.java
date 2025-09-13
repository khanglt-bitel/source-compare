package com.example.sourcecompare;

/**
 * Simple container for file metadata used during comparisons.
 */
public class FileInfo {
    private final String name;
    private final String content;

    public FileInfo(String name, String content) {
        this.name = name;
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }
}

