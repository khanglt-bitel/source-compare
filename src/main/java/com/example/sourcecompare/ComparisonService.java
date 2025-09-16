package com.example.sourcecompare;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ComparisonService {
    private static final Logger log = LogManager.getLogger(ComparisonService.class);
    @Autowired
    private GoogleFormatService googleFormatService;
    @Autowired
    private DecompileService decompileService;
    @Autowired
    private EclipseFormatService eclipseFormatService;

    public ComparisonResult compare(
            MultipartFile leftZip, MultipartFile rightZip, ComparisonMode mode) throws IOException {
        return switch (mode) {
            case CLASS_VS_SOURCE -> compareClassToSource(leftZip, rightZip);
            case CLASS_VS_CLASS -> compareClassToClass(leftZip, rightZip);
            case SOURCE_VS_SOURCE -> compareSourceToSource(leftZip, rightZip);
        };
    }

    private ComparisonResult compareClassToSource(MultipartFile classZip, MultipartFile sourceZip)
            throws IOException {
        Map<String, FileInfo> leftRaw = decompileService.decompileClasses(classZip);
        Map<String, FileInfo> rightRaw = readSources(sourceZip);

        Map<String, FileInfo> left = new HashMap<>();
        for (Map.Entry<String, FileInfo> e : leftRaw.entrySet()) {
            String name = e.getKey().replace(".class", ".java");
            left.put(name, new FileInfo(name, googleFormatService.normalizeJava(e.getValue().getContent())));
        }
        Map<String, FileInfo> right = new HashMap<>();
        for (Map.Entry<String, FileInfo> e : rightRaw.entrySet()) {
            String name = e.getKey();
            right.put(name, new FileInfo(name, googleFormatService.normalizeJava(e.getValue().getContent())));
        }
        return diffFileMaps(left, right);
    }

    private ComparisonResult compareClassToClass(MultipartFile leftZip, MultipartFile rightZip) {
        CompletableFuture<Map<String, FileInfo>> leftFuture =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return decompileAndFormat(leftZip);
                            } catch (IOException e) {
                                throw new CompletionException(e);
                            }
                        });
        CompletableFuture<Map<String, FileInfo>> rightFuture =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return decompileAndFormat(rightZip);
                            } catch (IOException e) {
                                throw new CompletionException(e);
                            }
                        });
        Map<String, FileInfo> left = leftFuture.join();
        Map<String, FileInfo> right = rightFuture.join();
        return diffFileMaps(left, right);
    }

    private Map<String, FileInfo> decompileAndFormat(MultipartFile zip)
            throws IOException {
        long start = System.currentTimeMillis();
        Map<String, FileInfo> raw = decompileService.decompileClasses(zip);
        log.info("Step 1: decompileAndFormat raw:{}", 1.0 * (System.currentTimeMillis() - start) / 1000);
        Map<String, FileInfo> formatted = new HashMap<>();
        raw.values().stream()
                .map(fi -> eclipseFormatService.formatFile(fi.getName(), fi.getContent()))
                .forEach(fi -> formatted.put(fi.getName(), fi));
        log.info("Step 2: decompileAndFormat files:{}", 1.0 * (System.currentTimeMillis() - start) / 1000);
        return formatted;
    }

    private ComparisonResult compareSourceToSource(MultipartFile leftZip, MultipartFile rightZip)
            throws IOException {
        long start = System.currentTimeMillis();
        Map<String, FileInfo> leftRaw = readSources(leftZip);
        Map<String, FileInfo> rightRaw = readSources(rightZip);
        log.info("Step 1: Read files:{}", 1.0 * (System.currentTimeMillis() - start) / 1000);

        Map<String, FileInfo> left = new HashMap<>();
        leftRaw.values().stream()
                .map(fi -> eclipseFormatService.formatFile(fi.getName(), fi.getContent()))
                .forEach(fi -> left.put(fi.getName(), fi));

        log.info("Step 2: Execute left:{}", 1.0 * (System.currentTimeMillis() - start) / 1000);

        Map<String, FileInfo> right = new HashMap<>();
        rightRaw.values().stream()
                .map(fi -> eclipseFormatService.formatFile(fi.getName(), fi.getContent()))
                .forEach(fi -> right.put(fi.getName(), fi));
        log.info("Step 3: Execute right:{}", 1.0 * (System.currentTimeMillis() - start) / 1000);

        ComparisonResult result = diffFileMaps(left, right);

        log.info("Step 4: Compare:{}", 1.0 * (System.currentTimeMillis() - start) / 1000);

        return result;
    }

    private Map<String, FileInfo> readSources(MultipartFile zip) throws IOException {
        Map<String, FileInfo> result = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
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

    private ComparisonResult diffFileMaps(Map<String, FileInfo> left, Map<String, FileInfo> right) {
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
                    modified.put(name, new FileInfo[]{l, r});
                } else {
                    unchanged.add(name);
                }
            }
        }
        log.info("Step 1: diffFileMaps:{}", (System.currentTimeMillis() - start) / 1000);

        Map<String, FileInfo[]> renames = new LinkedHashMap<>();

        Map<String, Deque<String>> addedByHash = new HashMap<>();
        for (Map.Entry<String, FileInfo> addEntry : added.entrySet()) {
            addedByHash
                    .computeIfAbsent(addEntry.getValue().getHash(), key -> new ArrayDeque<>())
                    .add(addEntry.getKey());
        }

        Iterator<Map.Entry<String, FileInfo>> delIt = deleted.entrySet().iterator();
        while (delIt.hasNext()) {
            Map.Entry<String, FileInfo> del = delIt.next();
            Deque<String> candidates = addedByHash.get(del.getValue().getHash());
            while (candidates != null && !candidates.isEmpty()) {
                String candidateName = candidates.removeFirst();
                FileInfo candidateInfo = added.remove(candidateName);
                if (candidateInfo != null) {
                    renames.put(
                            del.getKey() + "->" + candidateName,
                            new FileInfo[]{del.getValue(), candidateInfo});
                    delIt.remove();
                    if (candidates.isEmpty()) {
                        addedByHash.remove(del.getValue().getHash());
                    }
                    break;
                }
            }
        }

        delIt = deleted.entrySet().iterator();
        while (delIt.hasNext()) {
            Map.Entry<String, FileInfo> del = delIt.next();
            String bestName = null;
            double bestScore = 0.0;
            for (Map.Entry<String, FileInfo> add : added.entrySet()) {
                if (!isPotentialRename(del.getValue(), add.getValue())) {
                    continue;
                }
                double score = similarity(del.getValue().getContent(), add.getValue().getContent());
                if (score > 0.85 && score > bestScore) {
                    bestScore = score;
                    bestName = add.getKey();
                }
            }
            if (bestName != null) {
                FileInfo rightInfo = added.remove(bestName);
                renames.put(del.getKey() + "->" + bestName, new FileInfo[]{del.getValue(), rightInfo});
                delIt.remove();
            }
        }
        log.info("Step 2: diffFileMaps:{}", (System.currentTimeMillis() - start) / 1000);

        Map<String, DiffInfo> addedDiffs = new LinkedHashMap<>();
        for (Map.Entry<String, FileInfo> e : added.entrySet()) {
            addedDiffs.put(
                    e.getKey(), new DiffInfo(generateDiff(e.getKey(), "", e.getValue().getContent())));
        }
        Map<String, DiffInfo> deletedDiffs = new LinkedHashMap<>();
        for (Map.Entry<String, FileInfo> e : deleted.entrySet()) {
            deletedDiffs.put(
                    e.getKey(), new DiffInfo(generateDiff(e.getKey(), e.getValue().getContent(), "")));
        }
        Map<String, DiffInfo> modifiedDiffs = new LinkedHashMap<>();
        for (Map.Entry<String, FileInfo[]> e : modified.entrySet()) {
            modifiedDiffs.put(
                    e.getKey(),
                    new DiffInfo(
                            generateDiff(
                                    e.getKey(), e.getValue()[0].getContent(), e.getValue()[1].getContent())));
        }
        log.info("Step 3: diffFileMaps:{}", (System.currentTimeMillis() - start) / 1000);
        List<RenameInfo> renamedDiffs = new ArrayList<>();
        for (Map.Entry<String, FileInfo[]> e : renames.entrySet()) {
            String[] names = e.getKey().split("->", 2);
            renamedDiffs.add(
                    new RenameInfo(
                            names[0],
                            names[1],
                            generateDiff(
                                    names[1], e.getValue()[0].getContent(), e.getValue()[1].getContent())));
        }
        renamedDiffs.sort(Comparator.comparing(RenameInfo::getTo));
        log.info("Step 4: diffFileMaps:{}", (System.currentTimeMillis() - start) / 1000);
        return new ComparisonResult(
                addedDiffs, deletedDiffs, modifiedDiffs, renamedDiffs, unchanged);
    }

    private boolean isPotentialRename(FileInfo leftInfo, FileInfo rightInfo) {
        int leftSize = leftInfo.getNormalizedSize();
        int rightSize = rightInfo.getNormalizedSize();
        int diff = Math.abs(leftSize - rightSize);
        int max = Math.max(leftSize, rightSize);
        if (max == 0) {
            return true;
        }
        int threshold = Math.max(80, (int) (max * 0.35));
        return diff <= threshold;
    }

    private double similarity(String a, String b) {
        List<String> aLines = Arrays.asList(a.split("\\R"));
        List<String> bLines = Arrays.asList(b.split("\\R"));
        if (aLines.isEmpty() && bLines.isEmpty()) {
            return 1.0;
        }
        Patch<String> patch = DiffUtils.diff(aLines, bLines);
        int total = Math.max(aLines.size(), bLines.size());
        int changes =
                patch.getDeltas().stream()
                        .mapToInt(d -> Math.max(d.getSource().size(), d.getTarget().size()))
                        .sum();
        return 1.0 - (double) changes / total;
    }


    private String generateDiff(String fileName, String original, String revised) {
        List<String> originalLines = Arrays.asList(original.split("\\R"));
        List<String> revisedLines = Arrays.asList(revised.split("\\R"));
        Patch<String> patch = DiffUtils.diff(originalLines, revisedLines);
        List<String> unified =
                UnifiedDiffUtils.generateUnifiedDiff(
                        fileName + "_orig", fileName + "_rev", originalLines, patch, 0);
        return String.join(System.lineSeparator(), unified) + System.lineSeparator();
    }
}
