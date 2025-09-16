package com.example.sourcecompare;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileInfoTest {

    @Test
    void computesHashFromNormalizedContent() {
        String content = "line1  \r\n line2\t\n";
        FileInfo info = new FileInfo("Test.java", content);

        String expectedNormalized = "line1\n line2";
        assertEquals(sha1(expectedNormalized), info.getHash());
        assertEquals(expectedNormalized.length(), info.getNormalizedSize());
    }

    @Test
    void identicalContentSharesHash() {
        FileInfo left = new FileInfo("Left.java", "class Test {}");
        FileInfo right = new FileInfo("Right.java", "class Test {}");

        assertEquals(left.getHash(), right.getHash());
        assertEquals(left.getNormalizedSize(), right.getNormalizedSize());
    }

    @Test
    void emptyContentProducesStableHash() {
        FileInfo info = new FileInfo("Empty.java", "");
        assertEquals(sha1(""), info.getHash());
        assertEquals(0, info.getNormalizedSize());
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
