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
    Map<String, String> left = decompileClasses(classZip);
    Map<String, String> right = readSources(sourceZip);

    StringBuilder allDiffs = new StringBuilder();
    for (String classPath : left.keySet()) {
      String javaPath = classPath.replace(".class", ".java");
      String leftCode = normalizeJava(left.get(classPath));
      String rightCode = normalizeJava(right.getOrDefault(javaPath, ""));
      allDiffs.append(generateDiff(javaPath, rightCode, leftCode));
    }
    return allDiffs.toString();
  }

  private String compareClassToClass(MultipartFile leftZip, MultipartFile rightZip)
      throws IOException {
    Map<String, String> left = classStructures(leftZip);
    Map<String, String> right = classStructures(rightZip);

    StringBuilder allDiffs = new StringBuilder();
    Set<String> allNames = new TreeSet<>();
    allNames.addAll(left.keySet());
    allNames.addAll(right.keySet());
    for (String name : allNames) {
      String leftStruct = left.getOrDefault(name, "");
      String rightStruct = right.getOrDefault(name, "");
      allDiffs.append(generateDiff(name, leftStruct, rightStruct));
    }
    return allDiffs.toString();
  }

  private Map<String, String> decompileClasses(MultipartFile zip) throws IOException {
    Map<String, String> result = new HashMap<>();
    try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
          byte[] bytes = zis.readAllBytes();
          result.put(entry.getName(), decompile(bytes));
        }
      }
    }
    return result;
  }

  private Map<String, String> readSources(MultipartFile zip) throws IOException {
    Map<String, String> result = new HashMap<>();
    try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory() && entry.getName().endsWith(".java")) {
          result.put(entry.getName(), new String(zis.readAllBytes()));
        }
      }
    }
    return result;
  }

  private Map<String, String> classStructures(MultipartFile zip) throws IOException {
    Map<String, String> result = new HashMap<>();
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
          result.put(entry.getName(), struct);
        }
      }
    }
    return result;
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
    } catch (FormatterException e) {
      // keep unformatted source
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
