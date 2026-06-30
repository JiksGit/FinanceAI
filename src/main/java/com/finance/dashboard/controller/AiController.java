package com.finance.dashboard.controller;

import com.finance.dashboard.dto.request.AiAnalyzeRequest;
import com.finance.dashboard.dto.response.AiAnalyzeResponse;
import com.finance.dashboard.dto.response.ChatHistoryResponse;
import com.finance.dashboard.security.UserPrincipal;
import com.finance.dashboard.service.AiAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAnalysisService aiAnalysisService;

    @PostMapping("/analyze")
    public AiAnalyzeResponse analyze(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AiAnalyzeRequest request
    ) {
        return aiAnalysisService.analyze(principal.getUserId(), request);
    }

    @GetMapping("/history")
    public List<ChatHistoryResponse> getHistory(@AuthenticationPrincipal UserPrincipal principal) {
        return aiAnalysisService.getHistory(principal.getUserId());
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory(@AuthenticationPrincipal UserPrincipal principal) {
        aiAnalysisService.clearHistory(principal.getUserId());
        return ResponseEntity.ok().build();
    }
}
