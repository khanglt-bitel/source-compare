package com.example.sourcecompare.domain;

import java.util.List;

/**
 * Summary of timing information for a comparison run.
 */
public class ComparisonTiming {
    private final List<StepTiming> steps;
    private final double totalDurationSeconds;

    public ComparisonTiming(List<StepTiming> steps, double totalDurationSeconds) {
        this.steps = steps;
        this.totalDurationSeconds = totalDurationSeconds;
    }

    public List<StepTiming> getSteps() {
        return steps;
    }

    public double getTotalDurationSeconds() {
        return totalDurationSeconds;
    }
}

