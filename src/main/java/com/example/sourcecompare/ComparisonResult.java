package com.example.sourcecompare;

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
  private Map<String, DiffInfo> renamed;

  public ComparisonResult(
      Map<String, DiffInfo> added,
      Map<String, DiffInfo> deleted,
      Map<String, DiffInfo> modified,
      Map<String, DiffInfo> renamed) {
    this.added = added;
    this.deleted = deleted;
    this.modified = modified;
    this.renamed = renamed;
  }

  /** Combines all diff segments into a single unified diff string. */
  public String toUnifiedDiff() {
    StringBuilder allDiffs = new StringBuilder();
    added.forEach(
        (name, diff) -> {
          allDiffs.append("### Added ").append(name).append(System.lineSeparator());
          allDiffs.append(diff.getDiff());
        });
    deleted.forEach(
        (name, diff) -> {
          allDiffs.append("### Deleted ").append(name).append(System.lineSeparator());
          allDiffs.append(diff.getDiff());
        });
    modified.forEach(
        (name, diff) -> {
          allDiffs.append("### Modified ").append(name).append(System.lineSeparator());
          allDiffs.append(diff.getDiff());
        });
    renamed.forEach(
        (names, diff) -> {
          String[] parts = names.split("->", 2);
          allDiffs
              .append("### Renamed ")
              .append(parts[0])
              .append(" -> ")
              .append(parts[1])
              .append(System.lineSeparator());
          allDiffs.append(diff.getDiff());
        });
    return allDiffs.toString();
  }
}

