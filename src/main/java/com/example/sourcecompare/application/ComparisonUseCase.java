package com.example.sourcecompare.application;

import com.example.sourcecompare.domain.ArchiveInput;
import com.example.sourcecompare.domain.ComparisonRequest;
import com.example.sourcecompare.domain.ComparisonResult;
import com.example.sourcecompare.domain.DiffInfo;
import com.example.sourcecompare.domain.FileInfo;
import com.example.sourcecompare.domain.RenameInfo;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ComparisonUseCase {
    private static final Logger log = LogManager.getLogger(ComparisonUseCase.class);

    private final ArchiveDecompiler archiveDecompiler;
    private final JavaSourceNormalizer javaSourceNormalizer;
    private final SourceFormatter sourceFormatter;
    private final DiffRenderer diffRenderer;

    public ComparisonUseCase(
            ArchiveDecompiler archiveDecompiler,
            JavaSourceNormalizer javaSourceNormalizer,
            SourceFormatter sourceFormatter,
            DiffRenderer diffRenderer) {
        this.archiveDecompiler = archiveDecompiler;
        this.javaSourceNormalizer = javaSourceNormalizer;
        this.sourceFormatter = sourceFormatter;
        this.diffRenderer = diffRenderer;
    }

    public ComparisonResult compare(ComparisonRequest request) throws IOException {
        int normalizedContextSize = Math.max(0, request.contextSize());
        return switch (request.mode()) {
            case CLASS_VS_SOURCE ->
                    compareClassToSource(
                            request.left(),
                            request.right(),
                            normalizedContextSize,
                            request.includeUnchanged());
            case CLASS_VS_CLASS ->
                    compareClassToClass(
                            request.left(),
                            request.right(),
                            normalizedContextSize,
                            request.includeUnchanged());
            case SOURCE_VS_SOURCE ->
                    compareSourceToSource(
                            request.left(),
                            request.right(),
                            normalizedContextSize,
                            request.includeUnchanged());
        };
    }

    private ComparisonResult compareClassToSource(
            ArchiveInput classArchive,
            ArchiveInput sourceArchive,
            int contextSize,
            boolean includeUnchanged)
            throws IOException {
        Map<String, FileInfo> leftRaw = archiveDecompiler.decompileClasses(classArchive);
        Map<String, FileInfo> rightRaw = readSources(sourceArchive);

        Map<String, FileInfo> left = new HashMap<>();
        for (Map.Entry<String, FileInfo> e : leftRaw.entrySet()) {
            String name = e.getKey().replace(".class", ".java");
            left.put(
                    name,
                    new FileInfo(
                            name,
                            javaSourceNormalizer.normalizeJava(e.getValue().getContent())));
        }
        Map<String, FileInfo> right = new HashMap<>();
        for (Map.Entry<String, FileInfo> e : rightRaw.entrySet()) {
            String name = e.getKey();
            right.put(
                    name,
                    new FileInfo(
                            name,
                            javaSourceNormalizer.normalizeJava(e.getValue().getContent())));
        }
        return diffFileMaps(left, right, contextSize, includeUnchanged);
    }

    private ComparisonResult compareClassToClass(
            ArchiveInput leftArchive,
            ArchiveInput rightArchive,
            int contextSize,
            boolean includeUnchanged) {
        CompletableFuture<Map<String, FileInfo>> leftFuture =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return decompileAndFormat(leftArchive);
                            } catch (IOException e) {
                                throw new CompletionException(e);
                            }
                        });
        CompletableFuture<Map<String, FileInfo>> rightFuture =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return decompileAndFormat(rightArchive);
                            } catch (IOException e) {
                                throw new CompletionException(e);
                            }
                        });
        Map<String, FileInfo> left = leftFuture.join();
        Map<String, FileInfo> right = rightFuture.join();
        return diffFileMaps(left, right, contextSize, includeUnchanged);
    }

    private Map<String, FileInfo> decompileAndFormat(ArchiveInput archive) throws IOException {
        long start = System.currentTimeMillis();
        Map<String, FileInfo> raw = archiveDecompiler.decompileClasses(archive);
        log.info("Step 1: decompileAndFormat raw:{}", 1.0 * (System.currentTimeMillis() - start) / 1000);
        Map<String, FileInfo> formatted = new HashMap<>();
        raw.values().stream()
                .map(fi -> sourceFormatter.formatFile(fi.getName(), fi.getContent()))
                .forEach(fi -> formatted.put(fi.getName(), fi));
        log.info("Step 2: decompileAndFormat files:{}", 1.0 * (System.currentTimeMillis() - start) / 1000);
        return formatted;
    }

    private ComparisonResult compareSourceToSource(
            ArchiveInput leftArchive,
            ArchiveInput rightArchive,
            int contextSize,
            boolean includeUnchanged)
            throws IOException {
        long start = System.currentTimeMillis();
        Map<String, FileInfo> leftRaw = readSources(leftArchive);
        Map<String, FileInfo> rightRaw = readSources(rightArchive);
        log.info("Step 1: Read files:{}", 1.0 * (System.currentTimeMillis() - start) / 1000);

        Map<String, FileInfo> left = new HashMap<>();
        leftRaw.values().stream()
                .map(fi -> sourceFormatter.formatFile(fi.getName(), fi.getContent()))
                .forEach(fi -> left.put(fi.getName(), fi));

        log.info("Step 2: Execute left:{}", 1.0 * (System.currentTimeMillis() - start) / 1000);

        Map<String, FileInfo> right = new HashMap<>();
        rightRaw.values().stream()
                .map(fi -> sourceFormatter.formatFile(fi.getName(), fi.getContent()))
                .forEach(fi -> right.put(fi.getName(), fi));
        log.info("Step 3: Execute right:{}", 1.0 * (System.currentTimeMillis() - start) / 1000);

        ComparisonResult result = diffFileMaps(left, right, contextSize, includeUnchanged);

        log.info("Step 4: Compare:{}", 1.0 * (System.currentTimeMillis() - start) / 1000);

        return result;
    }

    private Map<String, FileInfo> readSources(ArchiveInput archive) throws IOException {
        Map<String, FileInfo> result = new HashMap<>();
        try (InputStream inputStream = archive.openStream();
                ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".java")) {
                    String name = entry.getName();
                    result.put(name, new FileInfo(name, new String(zis.readAllBytes())));
                }
            }
        }
        return result;
    }

    private ComparisonResult diffFileMaps(
            Map<String, FileInfo> left,
            Map<String, FileInfo> right,
            int contextSize,
            boolean includeUnchanged) {
        long start = System.currentTimeMillis();
        Map<String, FileInfo> added = new LinkedHashMap<>();
        Map<String, FileInfo> deleted = new LinkedHashMap<>();
        Map<String, FileInfo[]> modified = new LinkedHashMap<>();
        List<String> unchanged = new ArrayList<>();

        Set<String> allNames = new TreeSet<>();
        allNames.addAll(left.keySet());
        allNames.addAll(right.keySet());
        for (String name : allNames) {
            boolean inLeft = left.containsKey(name);
            boolean inRight = right.containsKey(name);
            if (inLeft && !inRight) {
                deleted.put(name, left.get(name));
            } else if (!inLeft && inRight) {
                added.put(name, right.get(name));
            } else {
                FileInfo l = left.get(name);
                FileInfo r = right.get(name);
                if (!Objects.equals(l.getContent(), r.getContent())) {
                    modified.put(name, new FileInfo[] {l, r});
                } else if (includeUnchanged) {
                    unchanged.add(name);
                }
            }
        }
        log.info("Step 1: diffFileMaps:{}", (System.currentTimeMillis() - start) / 1000);

        Map<String, FileInfo[]> renames = new LinkedHashMap<>();
        Iterator<Map.Entry<String, FileInfo>> delIt = deleted.entrySet().iterator();
        while (delIt.hasNext()) {
            Map.Entry<String, FileInfo> del = delIt.next();
            FileInfo leftInfo = del.getValue();
            String bestName = null;
            double bestScore = 0.0;
            for (Map.Entry<String, FileInfo> add : added.entrySet()) {
                FileInfo rightInfo = add.getValue();
                if (!isRenameCandidate(leftInfo, rightInfo)) {
                    continue;
                }
                double score = similarity(leftInfo.getContent(), rightInfo.getContent());
                if (score > 0.85 && score > bestScore) {
                    bestScore = score;
                    bestName = add.getKey();
                }
            }
            if (bestName != null) {
                FileInfo rightInfo = added.remove(bestName);
                renames.put(del.getKey() + "->" + bestName, new FileInfo[] {leftInfo, rightInfo});
                delIt.remove();
            }
        }
        log.info("Step 2: diffFileMaps:{}", (System.currentTimeMillis() - start) / 1000);

        Map<String, DiffInfo> addedDiffs = new LinkedHashMap<>();
        for (Map.Entry<String, FileInfo> e : added.entrySet()) {
            addedDiffs.put(
                    e.getKey(),
                    new DiffInfo(
                            diffRenderer.render(
                                    e.getKey(),
                                    "",
                                    e.getValue().getContent(),
                                    contextSize,
                                    ArchiveDecompiler.CONTENT_NOT_READ)));
        }
        Map<String, DiffInfo> deletedDiffs = new LinkedHashMap<>();
        for (Map.Entry<String, FileInfo> e : deleted.entrySet()) {
            deletedDiffs.put(
                    e.getKey(),
                    new DiffInfo(
                            diffRenderer.render(
                                    e.getKey(),
                                    e.getValue().getContent(),
                                    "",
                                    contextSize,
                                    ArchiveDecompiler.CONTENT_NOT_READ)));
        }
        Map<String, DiffInfo> modifiedDiffs = new LinkedHashMap<>();
        for (Map.Entry<String, FileInfo[]> e : modified.entrySet()) {
            modifiedDiffs.put(
                    e.getKey(),
                    new DiffInfo(
                            diffRenderer.render(
                                    e.getKey(),
                                    e.getValue()[0].getContent(),
                                    e.getValue()[1].getContent(),
                                    contextSize,
                                    ArchiveDecompiler.CONTENT_NOT_READ)));
        }
        log.info("Step 3: diffFileMaps:{}", (System.currentTimeMillis() - start) / 1000);
        List<RenameInfo> renamedDiffs = new ArrayList<>();
        for (Map.Entry<String, FileInfo[]> e : renames.entrySet()) {
            String[] names = e.getKey().split("->", 2);
            renamedDiffs.add(
                    new RenameInfo(
                            names[0],
                            names[1],
                            diffRenderer.render(
                                    names[1],
                                    e.getValue()[0].getContent(),
                                    e.getValue()[1].getContent(),
                                    contextSize,
                                    ArchiveDecompiler.CONTENT_NOT_READ)));
        }
        renamedDiffs.sort(Comparator.comparing(RenameInfo::getTo));
        log.info("Step 4: diffFileMaps:{}", (System.currentTimeMillis() - start) / 1000);
        return new ComparisonResult(
                addedDiffs,
                deletedDiffs,
                modifiedDiffs,
                renamedDiffs,
                includeUnchanged ? unchanged : null);
    }

    private double similarity(String a, String b) {
        List<String> aLines = Arrays.asList(a.split("\\R"));
        List<String> bLines = Arrays.asList(b.split("\\R"));
        double firstHundredSimilarity = calculateSimilarity(aLines, bLines, 100);
        if (firstHundredSimilarity < 0.2) {
            return firstHundredSimilarity;
        }
        return calculateSimilarity(aLines, bLines, -1);
    }

    private double calculateSimilarity(List<String> aLines, List<String> bLines, int limit) {
        List<String> leftLines = aLines;
        List<String> rightLines = bLines;
        if (limit > 0) {
            leftLines = aLines.subList(0, Math.min(limit, aLines.size()));
            rightLines = bLines.subList(0, Math.min(limit, bLines.size()));
        }
        if (leftLines.isEmpty() && rightLines.isEmpty()) {
            return 1.0;
        }
        Patch<String> patch = DiffUtils.diff(leftLines, rightLines);
        int total = Math.max(leftLines.size(), rightLines.size());
        if (total == 0) {
            return 1.0;
        }
        int changes =
                patch.getDeltas().stream()
                        .mapToInt(d -> Math.max(d.getSource().size(), d.getTarget().size()))
                        .sum();
        return 1.0 - (double) changes / total;
    }

    private boolean isRenameCandidate(FileInfo leftInfo, FileInfo rightInfo) {
        if (!Objects.equals(getExtension(leftInfo.getName()), getExtension(rightInfo.getName()))) {
            return false;
        }
        return hasSimilarSize(leftInfo.getContent(), rightInfo.getContent());
    }

    private String getExtension(String fileName) {
        int lastSeparator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > lastSeparator) {
            return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private boolean hasSimilarSize(String leftContent, String rightContent) {
        int leftLength = leftContent.length();
        int rightLength = rightContent.length();
        int maxLength = Math.max(leftLength, rightLength);
        if (maxLength == 0) {
            return true;
        }
        return Math.abs(leftLength - rightLength) <= maxLength * 0.2;
    }
}
