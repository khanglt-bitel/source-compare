package com.example.sourcecompare.web;

import com.example.sourcecompare.application.ComparisonResultPersistenceService;
import com.example.sourcecompare.application.ComparisonUseCase;
import com.example.sourcecompare.domain.ComparisonMode;
import com.example.sourcecompare.domain.ComparisonRequest;
import com.example.sourcecompare.domain.ComparisonResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;

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
            HttpServletRequest httpRequest)
            throws IOException {
        ComparisonRequest request =
                new ComparisonRequest(
                        archiveInputAdapter.adapt(leftZip),
                        archiveInputAdapter.adapt(rightZip),
                        mode,
                        contextSize,
                        showUnchanged);
        ComparisonResult result = comparisonUseCase.compare(request);
        String comparisonName =
                String.format(
                        "%s vs %s",
                        archiveInputAdapter.describeFilenames(leftZip),
                        archiveInputAdapter.describeFilenames(rightZip));
        long id =
                comparisonResultPersistenceService.saveComparison(
                        comparisonName, httpRequest.getRemoteAddr(), result);
        return "redirect:/compare-result/" + id;
    }

    @GetMapping("/compare-result/{id}")
    public String viewComparison(@PathVariable("id") long id, Model model) {
        var storedResult = comparisonResultPersistenceService.loadComparison(id);
        model.addAttribute("message", storedResult.name());
        model.addAttribute("result", storedResult.result());
        model.addAttribute("comparisonId", storedResult.id());
        model.addAttribute("ipRequest", storedResult.ipRequest());
        model.addAttribute("created", storedResult.created());
        return "diff";
    }
}
