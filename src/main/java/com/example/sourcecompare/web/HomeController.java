package com.example.sourcecompare.web;

import com.example.sourcecompare.application.ComparisonResultPersistenceService;
import com.example.sourcecompare.application.ComparisonUseCase;
import com.example.sourcecompare.domain.ComparisonMode;
import com.example.sourcecompare.domain.ComparisonRequest;
import com.example.sourcecompare.domain.ComparisonResult;
import com.example.sourcecompare.domain.ComparisonRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Controller
public class HomeController {
    private final ComparisonUseCase comparisonUseCase;
    private final MultipartArchiveInputAdapter archiveInputAdapter;
    private final ComparisonResultPersistenceService comparisonResultPersistenceService;

    public HomeController(
            ComparisonUseCase comparisonUseCase,
            MultipartArchiveInputAdapter archiveInputAdapter,
            ComparisonResultPersistenceService comparisonResultPersistenceService) {
        this.comparisonUseCase = comparisonUseCase;
        this.archiveInputAdapter = archiveInputAdapter;
        this.comparisonResultPersistenceService = comparisonResultPersistenceService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("message", "Source Compare Initialized");
        return "index";
    }

    @PostMapping("/compare")
    public String compare(
            @RequestParam("leftZip") MultipartFile[] leftZip,
            @RequestParam("rightZip") MultipartFile[] rightZip,
            @RequestParam(name = "mode", defaultValue = "CLASS_VS_CLASS") ComparisonMode mode,
            @RequestParam(name = "contextSize", defaultValue = "5") int contextSize,
            @RequestParam(name = "showUnchanged", defaultValue = "false") boolean showUnchanged,
            Model model)
            throws IOException {
        ComparisonRequest request =
                new ComparisonRequest(
                        archiveInputAdapter.adapt(leftZip),
                        archiveInputAdapter.adapt(rightZip),
                        mode,
                        contextSize,
                        showUnchanged);
        ComparisonResult result = comparisonUseCase.compare(request);
        model.addAttribute(
                "message",
                String.format(
                        "Compared %s and %s using %s",
                        archiveInputAdapter.describeFilenames(leftZip),
                        archiveInputAdapter.describeFilenames(rightZip),
                        mode));
        model.addAttribute("result", result);
        return "diff";
    }

    @PostMapping("/compare/save")
    public ResponseEntity<?> saveComparison(@RequestBody SaveComparisonRequest request) {
        try {
            ComparisonRecord saved =
                    comparisonResultPersistenceService.save(
                            request.getName(), request.getUser(), request.getResult());
            return ResponseEntity.ok(Map.of("id", saved.getId()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
