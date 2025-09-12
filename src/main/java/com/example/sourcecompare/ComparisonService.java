package com.example.sourcecompare;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.OutputSinkFactory.Sink;
import org.benf.cfr.reader.api.OutputSinkFactory.SinkClass;
import org.benf.cfr.reader.api.OutputSinkFactory.SinkType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ComparisonService {

  public String compare(MultipartFile leftZip, MultipartFile rightZip, ComparisonMode mode)
      throws IOException {
    return switch (mode) {
      case CLASS_VS_SOURCE -> compareClassToSource(leftZip, rightZip);
      case CLASS_VS_CLASS -> compareClassToClass(leftZip, rightZip);
    };
  }

  private String compareClassToSource(MultipartFile classZip, MultipartFile sourceZip)
      throws IOException {
    Map<String, FileInfo> leftRaw = decompileClasses(classZip);
    Map<String, FileInfo> rightRaw = readSources(sourceZip);

    Map<String, FileInfo> left = new HashMap<>();
    for (Map.Entry<String, FileInfo> e : leftRaw.entrySet()) {
      String name = e.getKey().replace(".class", ".java");
      left.put(name, new FileInfo(name, normalizeJava(e.getValue().getContent())));
    }
    Map<String, FileInfo> right = new HashMap<>();
    for (Map.Entry<String, FileInfo> e : rightRaw.entrySet()) {
      String name = e.getKey();
      right.put(name, new FileInfo(name, normalizeJava(e.getValue().getContent())));
    }
    return diffFileMaps(left, right);
  }

  private String compareClassToClass(MultipartFile leftZip, MultipartFile rightZip)
      throws IOException {
    Map<String, FileInfo> left = classStructures(leftZip);
    Map<String, FileInfo> right = classStructures(rightZip);
    return diffFileMaps(left, right);
  }

  private Map<String, FileInfo> decompileClasses(MultipartFile zip) throws IOException {
    Map<String, FileInfo> result = new HashMap<>();
    try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
          byte[] bytes = zis.readAllBytes();
          String name = entry.getName();
          result.put(name, new FileInfo(name, decompile(bytes)));
        }
      }
    }
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

  private Map<String, FileInfo> classStructures(MultipartFile zip) throws IOException {
    Map<String, FileInfo> result = new HashMap<>();
    try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
          byte[] bytes = zis.readAllBytes();
          ClassReader reader = new ClassReader(bytes);
          List<String> lines = new ArrayList<>();
          reader.accept(
              new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value) {
                  lines.add("FIELD " + name + " " + descriptor);
                  return super.visitField(access, name, descriptor, signature, value);
                }

                @Override
                public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                  lines.add("METHOD " + name + descriptor);
                  return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
              },
              0);
          Collections.sort(lines);
          String struct =
              "CLASS "
                  + reader.getClassName()
                  + System.lineSeparator()
                  + String.join(System.lineSeparator(), lines);
          String name = entry.getName();
          result.put(name, new FileInfo(name, struct));
        }
      }
    }
    return result;
  }

  private String diffFileMaps(Map<String, FileInfo> left, Map<String, FileInfo> right) {
    Map<String, FileInfo> added = new LinkedHashMap<>();
    Map<String, FileInfo> deleted = new LinkedHashMap<>();
    Map<String, FileInfo[]> modified = new LinkedHashMap<>();

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
        }
      }
    }

    Map<String, FileInfo[]> renames = new LinkedHashMap<>();
    Iterator<Map.Entry<String, FileInfo>> delIt = deleted.entrySet().iterator();
    while (delIt.hasNext()) {
      Map.Entry<String, FileInfo> del = delIt.next();
      String bestName = null;
      double bestScore = 0.0;
      for (Map.Entry<String, FileInfo> add : added.entrySet()) {
        double score = similarity(del.getValue().getContent(), add.getValue().getContent());
        if (score > 0.85 && score > bestScore) {
          bestScore = score;
          bestName = add.getKey();
        }
      }
      if (bestName != null) {
        FileInfo rightInfo = added.remove(bestName);
        renames.put(del.getKey() + "->" + bestName, new FileInfo[] {del.getValue(), rightInfo});
        delIt.remove();
      }
    }

    StringBuilder allDiffs = new StringBuilder();
    for (Map.Entry<String, FileInfo> e : added.entrySet()) {
      allDiffs.append("### Added ").append(e.getKey()).append(System.lineSeparator());
      allDiffs.append(generateDiff(e.getKey(), "", e.getValue().getContent()));
    }
    for (Map.Entry<String, FileInfo> e : deleted.entrySet()) {
      allDiffs.append("### Deleted ").append(e.getKey()).append(System.lineSeparator());
      allDiffs.append(generateDiff(e.getKey(), e.getValue().getContent(), ""));
    }
    for (Map.Entry<String, FileInfo[]> e : modified.entrySet()) {
      allDiffs.append("### Modified ").append(e.getKey()).append(System.lineSeparator());
      allDiffs.append(
          generateDiff(
              e.getKey(), e.getValue()[0].getContent(), e.getValue()[1].getContent()));
    }
    for (Map.Entry<String, FileInfo[]> e : renames.entrySet()) {
      String[] names = e.getKey().split("->", 2);
      allDiffs
          .append("### Renamed ")
          .append(names[0])
          .append(" -> ")
          .append(names[1])
          .append(System.lineSeparator());
      allDiffs.append(
          generateDiff(
              names[1], e.getValue()[0].getContent(), e.getValue()[1].getContent()));
    }
    return allDiffs.toString();
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

  private String decompile(byte[] classBytes) throws IOException {
    Path tempClass = Files.createTempFile("cfr", ".class");
    Files.write(tempClass, classBytes);
    StringBuilder out = new StringBuilder();
    OutputSinkFactory sink =
        new OutputSinkFactory() {
          @Override
          public List<SinkClass> getSupportedSinks(
              SinkType sinkType, Collection<SinkClass> collection) {
            return List.of(SinkClass.STRING);
          }

          @Override
          public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
            return t -> out.append(t).append(System.lineSeparator());
          }
        };
    CfrDriver driver = new CfrDriver.Builder().withOutputSink(sink).build();
    driver.analyse(Collections.singletonList(tempClass.toString()));
    Files.deleteIfExists(tempClass);
    return out.toString();
  }

  private String normalizeJava(String source) {
    try {
      source = new Formatter().formatSource(source);
    } catch (IllegalAccessError | FormatterException e) {
      // google-java-format needs access to internal JDK packages that may be restricted;
      // if unavailable, fall back to the original source without formatting.
    }
    return normalizeText(source);
  }

  private String normalizeHtml(String source) {
    Document doc = Jsoup.parse(source);
    doc.outputSettings().prettyPrint(true).indentAmount(2);
    return normalizeText(doc.outerHtml());
  }

  private String normalizeJsCss(String source) {
    return normalizeText(source);
  }

  private String normalizeText(String source) {
    String normalized = source.replace("\r\n", "\n").replace("\r", "\n");
    String[] lines = normalized.split("\n", -1);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      sb.append(lines[i].replaceAll("\\s+$", ""));
      if (i < lines.length - 1) {
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  private String hashContent(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(bytes);
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
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
