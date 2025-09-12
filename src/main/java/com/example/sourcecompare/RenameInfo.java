package com.example.sourcecompare;

import lombok.Getter;
import lombok.Setter;

/** Holds information about a renamed file and its diff. */
@Getter
@Setter
public class RenameInfo {
  private String from;
  private String to;
  private String diff;

  public RenameInfo(String from, String to, String diff) {
    this.from = from;
    this.to = to;
    this.diff = diff;
  }
}
