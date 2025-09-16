package com.example.sourcecompare.web;

import com.example.sourcecompare.domain.ArchiveInput;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MultipartArchiveInputAdapter {
    public ArchiveInput adapt(MultipartFile file) {
        return new ArchiveInput(file.getOriginalFilename(), file::getInputStream);
    }
}
