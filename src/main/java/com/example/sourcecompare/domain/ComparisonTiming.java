package com.example.sourcecompare.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Summary of timing information for a comparison run.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonTiming {
    private List<StepTiming> steps;
    private double totalDurationSeconds;
}

