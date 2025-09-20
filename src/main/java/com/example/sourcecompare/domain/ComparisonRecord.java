package com.example.sourcecompare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "comparison_results")
public class ComparisonRecord {
    @Id
    @SequenceGenerator(
            name = "comparison_results_seq",
            sequenceName = "COMPARISON_RESULTS_SEQ",
            allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "comparison_results_seq")
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "user_name", nullable = false)
    private String user;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String diff;

    public ComparisonRecord(String name, String user, String diff) {
        this.name = name;
        this.user = user;
        this.diff = diff;
    }
}

