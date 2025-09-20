package com.example.sourcecompare.infrastructure;

import com.example.sourcecompare.domain.ComparisonRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComparisonRecordRepository extends JpaRepository<ComparisonRecord, Long> {}

