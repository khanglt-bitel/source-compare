package com.example.sourcecompare.domain;

import java.util.Objects;

public record ComparisonRequest(
        ArchiveInput left,
        ArchiveInput right,
        ComparisonMode mode,
        int contextSize,
        boolean includeUnchanged) {
    public ComparisonRequest {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(mode, "mode");
    }
}
