package com.example.sourcecompare.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the elapsed time for a single comparison step.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StepTiming {
    private String label;
    private double durationSeconds;
}

