package com.example.sourcecompare.application;

import com.example.sourcecompare.domain.ArchiveInput;
import com.example.sourcecompare.domain.FileInfo;

import java.io.IOException;
import java.util.Map;

public interface ArchiveDecompiler {
    String CONTENT_NOT_READ = "CONTENT_NOT_READ";

    Map<String, FileInfo> decompileClasses(ArchiveInput archive) throws IOException;
}
