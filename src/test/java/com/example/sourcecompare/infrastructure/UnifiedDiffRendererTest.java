package com.example.sourcecompare.infrastructure;

import com.example.sourcecompare.application.ArchiveDecompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedDiffRendererTest {

    private final UnifiedDiffRenderer renderer = new UnifiedDiffRenderer();

    @Test
    void renderProducesFallbackWhenContentMatches() {
        String diff =
                renderer.render(
                        "Example.java",
                        ArchiveDecompiler.CONTENT_NOT_READ,
                        ArchiveDecompiler.CONTENT_NOT_READ,
                        3,
                        ArchiveDecompiler.CONTENT_NOT_READ);

        assertTrue(
                diff.contains("--- Example.java_orig"),
                "Diff should include original header when no textual differences exist.");
        assertTrue(
                diff.contains("+++ Example.java_rev"),
                "Diff should include revised header when no textual differences exist.");
        assertTrue(
                diff.contains("@@ -0,0 +0,0 @@"),
                "Diff should include synthetic hunk when no textual differences exist.");
        assertTrue(
                diff.contains("No textual differences available."),
                "Diff should include explanatory message when no textual differences exist.");
    }
}
