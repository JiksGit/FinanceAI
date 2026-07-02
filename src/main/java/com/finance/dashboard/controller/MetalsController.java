package com.finance.dashboard.controller;

import com.finance.dashboard.dto.response.MetalPriceResponse;
import com.finance.dashboard.service.MetalsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metals")
@RequiredArgsConstructor
public class MetalsController {

    private final MetalsService metalsService;

    @GetMapping("/prices")
    public List<MetalPriceResponse> getPrices() {
        return metalsService.getMetalPrices();
    }
}
