package com.example.sourcecompare.application;

import com.example.sourcecompare.domain.FileInfo;

public interface SourceFormatter {
    FileInfo formatFile(String name, String content);
}
