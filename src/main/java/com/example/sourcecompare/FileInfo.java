package com.example.sourcecompare;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Simple container for file metadata used during comparisons.
 */
public class FileInfo {
    private final String name;
    private final String content;
    private final String hash;
    private final int normalizedSize;

    public FileInfo(String name, String content) {
        this.name = name;
        this.content = content;
        String normalized = normalizeForHash(content);
        this.hash = computeSha1(normalized);
        this.normalizedSize = normalized.length();
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public String getHash() {
        return hash;
    }

    public int getNormalizedSize() {
        return normalizedSize;
    }

    private static String normalizeForHash(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder sb = new StringBuilder(normalized.length());
        for (int i = 0; i < lines.length; i++) {
            sb.append(rtrim(lines[i]));
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String rtrim(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        if (end == line.length()) {
            return line;
        }
        return line.substring(0, end);
    }

    private static String computeSha1(String normalized) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 algorithm is required to compute file hashes", e);
        }
    }
}

