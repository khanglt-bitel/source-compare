package com.example.sourcecompare.application;

import com.example.sourcecompare.domain.ComparisonRecord;
import com.example.sourcecompare.infrastructure.ComparisonRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ComparisonResultPersistenceService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(ComparisonResultPersistenceService.class);

    private final ComparisonRecordRepository repository;
    private final ObjectMapper objectMapper;

    public ComparisonResultPersistenceService(
            ComparisonRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public ComparisonRecord save(String name, String user, JsonNode result) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Comparison name is required");
        }
        if (!StringUtils.hasText(user)) {
            throw new IllegalArgumentException("User is required");
        }
        if (result == null || result.isNull()) {
            throw new IllegalArgumentException("Comparison result payload is required");
        }

        String diffAsString = serialize(result);
        ComparisonRecord record = new ComparisonRecord(name.trim(), user.trim(), diffAsString);
        return repository.save(record);
    }

    private String serialize(JsonNode result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            LOGGER.error("Failed to serialize comparison result", ex);
            throw new IllegalArgumentException("Unable to serialize comparison result", ex);
        }
    }
}

