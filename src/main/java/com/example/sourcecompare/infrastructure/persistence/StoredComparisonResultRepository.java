package com.example.sourcecompare.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredComparisonResultRepository
        extends JpaRepository<StoredComparisonResult, Long> {
    Page<StoredComparisonResult> findByNameContainingIgnoreCaseAndIpRequestContainingIgnoreCase(
            String name, String ipRequest, Pageable pageable);
}
