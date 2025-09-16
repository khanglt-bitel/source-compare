package com.example.sourcecompare.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Holds diff text for a file.
 */
@Getter
@Setter
public class DiffInfo {
    private String diff;

    public DiffInfo(String diff) {
        this.diff = diff;
    }
}

