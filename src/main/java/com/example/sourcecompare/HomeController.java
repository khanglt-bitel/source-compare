package com.example.sourcecompare;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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
            @RequestParam(name = "contextSize", defaultValue = "5") int contextSize,
            Model model)
            throws IOException {
        ComparisonResult result =
                comparisonService.compare(leftZip, rightZip, mode, contextSize);
        model.addAttribute(
                "message",
                String.format(
                        "Compared %s and %s using %s",
                        leftZip.getOriginalFilename(), rightZip.getOriginalFilename(), mode));
        model.addAttribute("result", result);
        return "diff";
    }
}
