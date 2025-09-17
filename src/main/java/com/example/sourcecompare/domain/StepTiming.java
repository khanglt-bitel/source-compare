package com.example.sourcecompare.domain;

/**
 * Represents the elapsed time for a single comparison step.
 */
public class StepTiming {
    private final String label;
    private final double durationSeconds;

    public StepTiming(String label, double durationSeconds) {
        this.label = label;
        this.durationSeconds = durationSeconds;
    }

    public String getLabel() {
        return label;
    }

    public double getDurationSeconds() {
        return durationSeconds;
    }
}

