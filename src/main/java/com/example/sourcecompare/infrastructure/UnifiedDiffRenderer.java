package com.example.sourcecompare.infrastructure;

import com.example.sourcecompare.application.DiffRenderer;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class UnifiedDiffRenderer implements DiffRenderer {
    private static final String NO_TEXTUAL_DIFFERENCES_MESSAGE = "No textual differences available.";

    @Override
    public String render(
            String fileName,
            String original,
            String revised,
            int contextSize,
            String unreadPlaceholder) {
        List<String> originalLines = Arrays.asList(original.split("\\R"));
        List<String> revisedLines = Arrays.asList(revised.split("\\R"));
        Patch<String> patch = DiffUtils.diff(originalLines, revisedLines);
        int safeContextSize = Math.max(0, contextSize);
        List<String> unified =
                UnifiedDiffUtils.generateUnifiedDiff(
                        fileName + "_orig",
                        fileName + "_rev",
                        originalLines,
                        patch,
                        safeContextSize);
        if (unified.isEmpty()) {
            String content =
                    original.equals(unreadPlaceholder)
                            ? original
                            : NO_TEXTUAL_DIFFERENCES_MESSAGE;
            unified =
                    List.of(
                            String.format("--- %s_orig", fileName),
                            String.format("+++ %s_rev", fileName),
                            "@@ -0,0 +0,0 @@",
                            " " + content);
        }
        return String.join(System.lineSeparator(), unified) + System.lineSeparator();
    }
}
