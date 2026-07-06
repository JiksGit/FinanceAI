package com.finance.dashboard.controller;

import com.finance.dashboard.entity.PriceAlert;
import com.finance.dashboard.security.UserPrincipal;
import com.finance.dashboard.service.PriceAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class PriceAlertController {

    private final PriceAlertService priceAlertService;

    @GetMapping
    public List<PriceAlert> getAlerts(@AuthenticationPrincipal UserPrincipal principal) {
        return priceAlertService.getAllAlerts(principal.getUserId());
    }

    @GetMapping("/unread")
    public List<PriceAlert> getUnread(@AuthenticationPrincipal UserPrincipal principal) {
        return priceAlertService.getUnreadAlerts(principal.getUserId());
    }

    @GetMapping("/count")
    public Map<String, Long> getUnreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("count", priceAlertService.getUnreadCount(principal.getUserId()));
    }

    @PutMapping("/{id}/read")
    public void markRead(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        priceAlertService.markRead(principal.getUserId(), id);
    }

    @PutMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        priceAlertService.markAllRead(principal.getUserId());
    }

    @PutMapping("/target/{stockCode}")
    public void setTarget(
            @PathVariable String stockCode,
            @RequestBody TargetPriceRequest body,
            @AuthenticationPrincipal UserPrincipal principal) {
        priceAlertService.setTargetPrice(principal.getUserId(), stockCode, body.targetPrice(), body.targetAbove());
    }

    @DeleteMapping("/target/{stockCode}")
    public void clearTarget(
            @PathVariable String stockCode,
            @AuthenticationPrincipal UserPrincipal principal) {
        priceAlertService.clearTargetPrice(principal.getUserId(), stockCode);
    }

    record TargetPriceRequest(BigDecimal targetPrice, Boolean targetAbove) {}
}
