package com.finance.dashboard.controller;

import com.finance.dashboard.dto.response.SignalResponse;
import com.finance.dashboard.service.SignalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lambda/EventBridge 등 서버 간 호출 전용 엔드포인트.
 * ServiceTokenAuthenticationFilter가 X-Service-Token 헤더를 검증한다.
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalController {

    private final SignalService signalService;

    @PostMapping("/signals/generate")
    public List<SignalResponse> generateSignals() {
        return signalService.generateSignals();
    }
}
