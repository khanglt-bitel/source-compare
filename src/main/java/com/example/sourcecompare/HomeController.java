package com.example.sourcecompare;

import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class HomeController {
  private final ComparisonService comparisonService;

  public HomeController(ComparisonService comparisonService) {
    this.comparisonService = comparisonService;
  }

  @GetMapping("/")
  public String index(Model model) {
    model.addAttribute("message", "Source Compare Initialized");
    return "index";
  }

  @PostMapping("/compare")
  public String compare(
      @RequestParam("leftZip") MultipartFile leftZip,
      @RequestParam("rightZip") MultipartFile rightZip,
      @RequestParam("mode") ComparisonMode mode,
      Model model)
      throws IOException {
    String diff = comparisonService.compare(leftZip, rightZip, mode);
    model.addAttribute(
        "message",
        String.format(
            "Compared %s and %s using %s",
            leftZip.getOriginalFilename(), rightZip.getOriginalFilename(), mode));
    model.addAttribute("diff", diff);
    return "diff";
  }
}
