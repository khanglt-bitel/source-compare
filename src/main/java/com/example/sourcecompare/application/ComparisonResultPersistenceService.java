package com.example.sourcecompare.application;

import com.example.sourcecompare.domain.ComparisonResult;
import com.example.sourcecompare.infrastructure.persistence.StoredComparisonResult;
import com.example.sourcecompare.infrastructure.persistence.StoredComparisonResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ComparisonResultPersistenceService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final List<MarkColorOption> MARK_COLOR_OPTIONS =
            List.of(
                    new MarkColorOption("royalblue", "Blue"),
                    new MarkColorOption("seagreen", "Green"),
                    new MarkColorOption("firebrick", "Red"),
                    new MarkColorOption("darkorange", "Orange"),
                    new MarkColorOption("mediumpurple", "Purple"),
                    new MarkColorOption("teal", "Teal"),
                    new MarkColorOption("palevioletred", "Pink"),
                    new MarkColorOption("slategray", "Gray"));

    private static final String DEFAULT_MARK_COLOR = MARK_COLOR_OPTIONS.get(0).value();

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
        entity.setMarkColor(DEFAULT_MARK_COLOR);
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
        String markColor = normalizeMarkColor(entity.getMarkColor());
        return new StoredComparisonResultView(
                entity.getId(),
                entity.getName(),
                entity.getIpRequest(),
                entity.getCreated(),
                markColor,
                describeMarkColor(markColor),
                result);
    }

    @Transactional(readOnly = true)
    public List<StoredComparisonResultSummary> loadRecentComparisons() {
        return searchComparisons(null, null, 0, DEFAULT_PAGE_SIZE).getContent();
    }

    @Transactional(readOnly = true)
    public Page<StoredComparisonResultSummary> searchComparisons(
            String nameFilter, String ipFilter, int page, int size) {
        String nameQuery = sanitizeFilter(nameFilter);
        String ipQuery = sanitizeFilter(ipFilter);
        Pageable pageable =
                PageRequest.of(sanitizePage(page), sanitizeSize(size), sortByCreatedDesc());

        return repository
                .findByNameContainingIgnoreCaseAndIpRequestContainingIgnoreCase(
                        nameQuery, ipQuery, pageable)
                .map(
                        result -> {
                            String markColor = normalizeMarkColor(result.getMarkColor());
                            return new StoredComparisonResultSummary(
                                    result.getId(),
                                    result.getName(),
                                    result.getIpRequest(),
                                    result.getCreated(),
                                    markColor,
                                    describeMarkColor(markColor));
                        });
    }

    @Transactional
    public void updateComparison(long id, String requesterIp, String markColor, String name) {
        StoredComparisonResult entity =
                repository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Comparison not found"));

        if (!Objects.equals(entity.getIpRequest(), requesterIp)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to edit this comparison");
        }

        if (markColor != null) {
            if (!isValidMarkColor(markColor)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid color value");
            }
            entity.setMarkColor(markColor);
        }

        if (name != null) {
            String sanitizedName = name.trim();
            if (sanitizedName.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name cannot be empty");
            }
            entity.setName(sanitizedName);
        }
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
            Long id,
            String name,
            String ipRequest,
            LocalDateTime created,
            String markColor,
            String markColorLabel,
            ComparisonResult result) {}

    public record StoredComparisonResultSummary(
            Long id,
            String name,
            String ipRequest,
            LocalDateTime created,
            String markColor,
            String markColorLabel) {}

    public record MarkColorOption(String value, String label) {}

    public List<MarkColorOption> getAvailableMarkColors() {
        return MARK_COLOR_OPTIONS;
    }

    public String getDefaultMarkColor() {
        return DEFAULT_MARK_COLOR;
    }

    public String describeMarkColor(String markColor) {
        return findMarkColorOption(markColor).orElse(MARK_COLOR_OPTIONS.get(0)).label();
    }

    public boolean isValidMarkColor(String value) {
        return findMarkColorOption(value).isPresent();
    }

    private String normalizeMarkColor(String value) {
        return findMarkColorOption(value).map(MarkColorOption::value).orElse(DEFAULT_MARK_COLOR);
    }

    private Optional<MarkColorOption> findMarkColorOption(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return MARK_COLOR_OPTIONS.stream().filter(option -> option.value().equals(value)).findFirst();
    }

    private Sort sortByCreatedDesc() {
        return Sort.by(Sort.Direction.DESC, "created");
    }

    private int sanitizePage(int page) {
        return Math.max(page, 0);
    }

    private int sanitizeSize(int requestedSize) {
        if (requestedSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    private String sanitizeFilter(String filter) {
        if (filter == null) {
            return "";
        }
        return filter.trim();
    }
}
