package com.finance.dashboard.service;

import com.finance.dashboard.entity.FavoriteStock;
import com.finance.dashboard.entity.KrxDailyPrice;
import com.finance.dashboard.entity.PriceAlert;
import com.finance.dashboard.exception.CustomException;
import com.finance.dashboard.exception.ErrorCode;
import com.finance.dashboard.repository.FavoriteStockRepository;
import com.finance.dashboard.repository.PriceAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAlertService {

    private final FavoriteStockRepository favoriteStockRepository;
    private final PriceAlertRepository priceAlertRepository;
    private final KrxService krxService;
    private final SimpMessagingTemplate messagingTemplate;

    // ── 목표가 설정 ──────────────────────────────────────────

    @Transactional
    public void setTargetPrice(Long userId, String stockCode, BigDecimal targetPrice, Boolean targetAbove) {
        FavoriteStock fav = favoriteStockRepository
                .findByUserIdAndStockSymbol(userId, stockCode)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));
        fav.updateTargetPrice(targetPrice, targetAbove);
    }

    @Transactional
    public void clearTargetPrice(Long userId, String stockCode) {
        FavoriteStock fav = favoriteStockRepository
                .findByUserIdAndStockSymbol(userId, stockCode)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));
        fav.updateTargetPrice(null, null);
    }

    // ── 알림 조회 ────────────────────────────────────────────

    public List<PriceAlert> getUnreadAlerts(Long userId) {
        return priceAlertRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
    }

    public List<PriceAlert> getAllAlerts(Long userId) {
        return priceAlertRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(Long userId) {
        return priceAlertRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long alertId) {
        PriceAlert alert = priceAlertRepository.findById(alertId)
                .orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_FOUND));
        if (!alert.getUserId().equals(userId)) throw new CustomException(ErrorCode.STOCK_NOT_FOUND);
        alert.markRead();
    }

    @Transactional
    public void markAllRead(Long userId) {
        priceAlertRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                .forEach(PriceAlert::markRead);
    }

    // ── 목표가 체크 스케줄러 (60초마다) ─────────────────────

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void checkPriceAlerts() {
        if (!isMarketHours()) return;

        List<FavoriteStock> targets = favoriteStockRepository.findAllWithTargetPrice();
        if (targets.isEmpty()) return;

        for (FavoriteStock fav : targets) {
            try {
                KrxDailyPrice price = krxService.getCurrentPrice(fav.getStockSymbol()).orElse(null);
                if (price == null || price.getClosePrice() <= 0) continue;

                BigDecimal current = BigDecimal.valueOf(price.getClosePrice());
                BigDecimal target = fav.getTargetPrice();
                boolean above = Boolean.TRUE.equals(fav.getTargetAbove());

                boolean triggered = above
                        ? current.compareTo(target) >= 0   // 목표가 이상
                        : current.compareTo(target) <= 0;  // 목표가 이하

                if (!triggered) continue;

                // 중복 알림 방지 (같은 목표가로 미읽 알림이 이미 있으면 스킵)
                if (priceAlertRepository.existsByUserIdAndStockSymbolAndTargetPriceAndReadFalse(
                        fav.getUserId(), fav.getStockSymbol(), target)) continue;

                PriceAlert alert = PriceAlert.builder()
                        .userId(fav.getUserId())
                        .stockSymbol(fav.getStockSymbol())
                        .stockName(fav.getStockName())
                        .targetPrice(target)
                        .triggeredPrice(current)
                        .targetAbove(above)
                        .build();
                priceAlertRepository.save(alert);

                // WebSocket 푸시
                Map<String, Object> msg = Map.of(
                        "type", "PRICE_ALERT",
                        "stockSymbol", fav.getStockSymbol(),
                        "stockName", fav.getStockName(),
                        "targetPrice", target,
                        "currentPrice", current,
                        "direction", above ? "이상" : "이하",
                        "userId", fav.getUserId()
                );
                messagingTemplate.convertAndSend("/topic/alerts/" + fav.getUserId(), msg);
                log.info("목표가 알림 발송: {} {} → {}", fav.getStockName(), target, current);

            } catch (Exception e) {
                log.warn("목표가 체크 실패 [{}]: {}", fav.getStockSymbol(), e.getMessage());
            }
        }
    }

    private boolean isMarketHours() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false;
        LocalTime t = now.toLocalTime();
        return t.isAfter(LocalTime.of(8, 59)) && t.isBefore(LocalTime.of(15, 36));
    }
}
