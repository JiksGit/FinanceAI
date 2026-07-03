package com.finance.dashboard.controller;

import com.finance.dashboard.dto.response.MetalHistoryResponse;
import com.finance.dashboard.dto.response.MetalPriceResponse;
import com.finance.dashboard.service.MetalsHistoryService;
import com.finance.dashboard.service.MetalsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metals")
@RequiredArgsConstructor
public class MetalsController {

    private final MetalsService metalsService;
    private final MetalsHistoryService metalsHistoryService;

    @GetMapping("/prices")
    public List<MetalPriceResponse> getPrices() {
        return metalsService.getMetalPrices();
    }

    @GetMapping("/{symbol}/history")
    public MetalHistoryResponse getHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "3mo") String range) {
        return metalsHistoryService.getHistory(symbol, range);
    }

    @GetMapping("/debug")
    public String debug() {
        return metalsService.getRawJson("XAU");
    }
}
