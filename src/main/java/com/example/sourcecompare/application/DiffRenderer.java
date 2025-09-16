package com.example.sourcecompare.application;

public interface DiffRenderer {
    String render(
            String fileName,
            String original,
            String revised,
            int contextSize,
            String unreadPlaceholder);
}
