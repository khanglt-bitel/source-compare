package com.example.sourcecompare;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.springframework.stereotype.Service;

@Service
public class EclipseFormatService {
    public FileInfo formatFile(String name, String content) {
        String nameFormat = name.replace(".class", ".java");
        String contentFormat;
        if (nameFormat.contains(".java")) {
            contentFormat = format(content);
        } else {
            contentFormat = content;
        }

        return new FileInfo(nameFormat, contentFormat);
    }

    public String format(String source) {
        CodeFormatter codeFormatter = ToolFactory.createCodeFormatter(null);
        TextEdit edit = codeFormatter.format(
                CodeFormatter.K_COMPILATION_UNIT, // or K_STATEMENTS for snippets
                source, 0, source.length(), 0, null);

        if (edit == null) {
            return source; // fallback
        }

        Document doc = new Document(source);
        try {
            edit.apply(doc);
        } catch (BadLocationException e) {
            return source;
        }
        return doc.get();
    }
}
