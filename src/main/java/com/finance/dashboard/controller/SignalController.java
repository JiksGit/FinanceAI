package com.finance.dashboard.controller;

import com.finance.dashboard.dto.response.SignalResponse;
import com.finance.dashboard.security.UserPrincipal;
import com.finance.dashboard.service.SignalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalController {

    private final SignalService signalService;

    @GetMapping
    public List<SignalResponse> getRecentSignals() {
        return signalService.getRecentSignals();
    }

    @GetMapping("/my")
    public List<SignalResponse> getMySignals(@AuthenticationPrincipal UserPrincipal principal) {
        return signalService.getMySignals(principal.getUserId());
    }

    @PostMapping("/generate")
    public List<SignalResponse> generate() {
        return signalService.generateSignals();
    }
}
