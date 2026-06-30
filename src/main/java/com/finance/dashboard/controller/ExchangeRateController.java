package com.finance.dashboard.controller;

import com.finance.dashboard.dto.response.ExchangeRateHistoryResponse;
import com.finance.dashboard.dto.response.ExchangeRateResponse;
import com.finance.dashboard.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping("/today")
    public ExchangeRateResponse getTodayRates() {
        return exchangeRateService.getTodayRates();
    }

    @GetMapping("/history")
    public ExchangeRateHistoryResponse getHistory(
            @RequestParam String currency,
            @RequestParam(defaultValue = "30") int days
    ) {
        return exchangeRateService.getHistory(currency, days);
    }
}
