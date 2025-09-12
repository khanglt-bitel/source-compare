package com.example.sourcecompare;

import java.util.Map;

/** Holds structured comparison results. */
public class ComparisonResult {
  private final Map<String, String> added;
  private final Map<String, String> deleted;
  private final Map<String, String> modified;
  private final Map<String, String> renamed;

  public ComparisonResult(
      Map<String, String> added,
      Map<String, String> deleted,
      Map<String, String> modified,
      Map<String, String> renamed) {
    this.added = added;
    this.deleted = deleted;
    this.modified = modified;
    this.renamed = renamed;
  }

  public Map<String, String> getAdded() {
    return added;
  }

  public Map<String, String> getDeleted() {
    return deleted;
  }

  public Map<String, String> getModified() {
    return modified;
  }

  public Map<String, String> getRenamed() {
    return renamed;
  }

  /** Combines all diff segments into a single unified diff string. */
  public String toUnifiedDiff() {
    StringBuilder allDiffs = new StringBuilder();
    added.forEach(
        (name, diff) -> {
          allDiffs.append("### Added ").append(name).append(System.lineSeparator());
          allDiffs.append(diff);
        });
    deleted.forEach(
        (name, diff) -> {
          allDiffs.append("### Deleted ").append(name).append(System.lineSeparator());
          allDiffs.append(diff);
        });
    modified.forEach(
        (name, diff) -> {
          allDiffs.append("### Modified ").append(name).append(System.lineSeparator());
          allDiffs.append(diff);
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
          allDiffs.append(diff);
        });
    return allDiffs.toString();
  }
}

