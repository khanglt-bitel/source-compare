package com.example.sourcecompare;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class HomeController {
  @GetMapping("/")
  public String index(Model model) {
    model.addAttribute("message", "Source Compare Initialized");
    return "index";
  }

  @PostMapping("/compare")
  public String compare(
      @RequestParam("leftZip") MultipartFile leftZip,
      @RequestParam("rightZip") MultipartFile rightZip,
      Model model)
      throws IOException {
    List<String> leftEntries = listZipEntries(leftZip);
    List<String> rightEntries = listZipEntries(rightZip);
    model.addAttribute(
        "message",
        String.format(
            "Uploaded %s (%d entries) and %s (%d entries)",
            leftZip.getOriginalFilename(),
            leftEntries.size(),
            rightZip.getOriginalFilename(),
            rightEntries.size()));
    return "index";
  }

  private List<String> listZipEntries(MultipartFile file) throws IOException {
    List<String> entries = new ArrayList<>();
    try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          entries.add(entry.getName());
        }
      }
    }
    return entries;
  }
}
