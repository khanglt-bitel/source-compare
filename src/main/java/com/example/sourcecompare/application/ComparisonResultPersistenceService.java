package com.example.sourcecompare.application;

import com.example.sourcecompare.domain.ComparisonResult;
import com.example.sourcecompare.infrastructure.persistence.StoredComparisonResult;
import com.example.sourcecompare.infrastructure.persistence.StoredComparisonResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class ComparisonResultPersistenceService {
    private final StoredComparisonResultRepository repository;
    private final ObjectMapper objectMapper;

    public ComparisonResultPersistenceService(
            StoredComparisonResultRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public long saveComparison(String name, String ipRequest, ComparisonResult result) {
        StoredComparisonResult entity = new StoredComparisonResult();
        entity.setName(name);
        entity.setIpRequest(ipRequest);
        entity.setDiffResultJson(toJson(result));
        StoredComparisonResult saved = repository.save(entity);
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public StoredComparisonResultView loadComparison(long id) {
        StoredComparisonResult entity =
                repository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Comparison not found"));
        ComparisonResult result = fromJson(entity.getDiffResultJson());
        return new StoredComparisonResultView(
                entity.getId(), entity.getName(), entity.getIpRequest(), entity.getCreated(), result);
    }

    private String toJson(ComparisonResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store comparison result", ex);
        }
    }

    private ComparisonResult fromJson(String json) {
        try {
            return objectMapper.readValue(json, ComparisonResult.class);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read comparison result", ex);
        }
    }

    public record StoredComparisonResultView(
            Long id, String name, String ipRequest, LocalDateTime created, ComparisonResult result) {}
}
