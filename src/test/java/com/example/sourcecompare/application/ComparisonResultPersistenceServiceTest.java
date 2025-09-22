package com.example.sourcecompare.application;

import com.example.sourcecompare.infrastructure.persistence.StoredComparisonResult;
import com.example.sourcecompare.infrastructure.persistence.StoredComparisonResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComparisonResultPersistenceServiceTest {

    @Mock private StoredComparisonResultRepository repository;

    private ComparisonResultPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ComparisonResultPersistenceService(repository, new ObjectMapper());
    }

    @Test
    void searchComparisonsReturnsMappedSummaries() {
        StoredComparisonResult entity = new StoredComparisonResult();
        entity.setId(42L);
        entity.setName("Sample comparison");
        entity.setIpRequest("10.0.0.5");
        entity.setCreated(LocalDateTime.of(2024, 3, 1, 12, 30));
        entity.setMarkColor("firebrick");

        PageImpl<StoredComparisonResult> page =
                new PageImpl<>(
                        List.of(entity),
                        PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "created")),
                        1);

        when(repository.findByNameContainingIgnoreCaseAndIpRequestContainingIgnoreCase(
                        eq("sample"), eq("10.0.0.5"), any(Pageable.class)))
                .thenReturn(page);

        Page<ComparisonResultPersistenceService.StoredComparisonResultSummary> result =
                service.searchComparisons(" sample", "10.0.0.5 ", 0, 5);

        assertThat(result.getTotalElements()).isEqualTo(1);
        ComparisonResultPersistenceService.StoredComparisonResultSummary summary =
                result.getContent().get(0);
        assertThat(summary.id()).isEqualTo(42L);
        assertThat(summary.name()).isEqualTo("Sample comparison");
        assertThat(summary.ipRequest()).isEqualTo("10.0.0.5");
        assertThat(summary.markColor()).isEqualTo("firebrick");
        assertThat(summary.markColorLabel()).isEqualTo("Red");
    }

    @Test
    void searchComparisonsSanitizesPagingAndFilters() {
        when(repository.findByNameContainingIgnoreCaseAndIpRequestContainingIgnoreCase(
                        any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.searchComparisons(null, null, -3, 0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository)
                .findByNameContainingIgnoreCaseAndIpRequestContainingIgnoreCase(
                        eq(""), eq(""), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort.Order createdOrder = pageable.getSort().getOrderFor("created");
        assertThat(createdOrder).isNotNull();
        assertThat(createdOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
