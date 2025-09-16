package com.example.sourcecompare;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparisonServiceTest {

    @Test
    void generateDiffProducesFallbackWhenContentMatches() throws Exception {
        ComparisonService service = new ComparisonService();
        Method method =
                ComparisonService.class.getDeclaredMethod(
                        "generateDiff", String.class, String.class, String.class, int.class);
        method.setAccessible(true);

        String diff =
                (String)
                        method.invoke(
                                service,
                                "Example.java",
                                "CONTENT_NOT_READ",
                                "CONTENT_NOT_READ",
                                3);

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
