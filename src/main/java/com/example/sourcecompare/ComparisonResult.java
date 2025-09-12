package com.example.sourcecompare;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** Holds structured comparison results. */
@Getter
@Setter
public class ComparisonResult {
  private Map<String, DiffInfo> added;
  private Map<String, DiffInfo> deleted;
  private Map<String, DiffInfo> modified;
  private List<RenameInfo> renamed;

  public ComparisonResult(
      Map<String, DiffInfo> added,
      Map<String, DiffInfo> deleted,
      Map<String, DiffInfo> modified,
      List<RenameInfo> renamed) {
    this.added = added;
    this.deleted = deleted;
    this.modified = modified;
    this.renamed = renamed;
  }

  // No unified diff aggregation; consumers should access the maps directly.
}

