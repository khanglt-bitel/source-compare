package com.example.sourcecompare;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Holds structured comparison results.
 */
@Getter
@Setter
public class ComparisonResult {
    private Map<String, DiffInfo> added;
    private Map<String, DiffInfo> deleted;
    private Map<String, DiffInfo> modified;
    private List<RenameInfo> renamed;
    private List<String> unchanged;

    public ComparisonResult(
            Map<String, DiffInfo> added,
            Map<String, DiffInfo> deleted,
            Map<String, DiffInfo> modified,
            List<RenameInfo> renamed,
            List<String> unchanged) {
        this.added = added;
        this.deleted = deleted;
        this.modified = modified;
        this.renamed = renamed;
        this.unchanged = unchanged;
    }

    // No unified diff aggregation; consumers should access the maps directly.
}

