package com.example.sourcecompare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoredComparisonResultRepository
        extends JpaRepository<StoredComparisonResult, Long> {
    List<StoredComparisonResult> findTop20ByOrderByCreatedDesc();
}
