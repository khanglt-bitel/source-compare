package com.example.sourcecompare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "COMPARE_RESULTS")
public class StoredComparisonResult {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "compare_result_sequence")
    @SequenceGenerator(
            name = "compare_result_sequence",
            sequenceName = "COMPARE_RESULT_SEQ",
            allocationSize = 1)
    private Long id;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "IP_REQUEST", nullable = false)
    private String ipRequest;

    @Column(name = "CREATED", insertable = false, updatable = false)
    private LocalDateTime created;

    @Lob
    @Column(name = "DIFF_RESULT", nullable = false)
    private String diffResultJson;

    @Column(name = "MARK_COLOR")
    private String markColor;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIpRequest() {
        return ipRequest;
    }

    public void setIpRequest(String ipRequest) {
        this.ipRequest = ipRequest;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public String getDiffResultJson() {
        return diffResultJson;
    }

    public void setDiffResultJson(String diffResultJson) {
        this.diffResultJson = diffResultJson;
    }

    public String getMarkColor() {
        return markColor;
    }

    public void setMarkColor(String markColor) {
        this.markColor = markColor;
    }
}
