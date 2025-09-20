package com.example.sourcecompare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredComparisonResultRepository
        extends JpaRepository<StoredComparisonResult, Long> {}
