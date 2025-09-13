package com.example.sourcecompare;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.objectweb.asm.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class GoogleFormatService {
    public String normalizeJava(String source) {
        try {
            source = new Formatter().formatSource(source);
        } catch (IllegalAccessError | FormatterException e) {
            e.printStackTrace();
            // google-java-format needs access to internal JDK packages that may be restricted;
            // if unavailable, fall back to the original source without formatting.
        }
        return normalizeText(source);
    }

    public String normalizeHtml(String source) {
        Document doc = Jsoup.parse(source);
        doc.outputSettings().prettyPrint(true).indentAmount(2);
        return normalizeText(doc.outerHtml());
    }

    public String normalizeJsCss(String source) {
        return normalizeText(source);
    }

    public String normalizeText(String source) {
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

    public String hashContent(byte[] bytes) {
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

    public Map<String, FileInfo> classStructures(MultipartFile zip) throws IOException {
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
}
