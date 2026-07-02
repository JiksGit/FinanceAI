package com.finance.dashboard.service;

import com.finance.dashboard.dto.response.PriceUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceUpdateScheduler {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36";
    private static final String TOPIC = "/topic/market-prices";

    private final SimpMessagingTemplate messagingTemplate;

    // 연결된 클라이언트 수 추적
    private final AtomicInteger connectedClients = new AtomicInteger(0);

    public void clientConnected() { connectedClients.incrementAndGet(); }
    public void clientDisconnected() { connectedClients.decrementAndGet(); }

    /** 장중(09:00~15:35 KST 평일)에만 15초마다 실행 */
    @Scheduled(fixedRate = 15_000)
    public void pushLivePrices() {
        // 연결된 클라이언트 없으면 스킵
        if (connectedClients.get() == 0) return;
        if (!isMarketHours()) return;

        try {
            List<PriceUpdateMessage> prices = new ArrayList<>();
            prices.addAll(scrapeMarketPage(0, "KOSPI", 1)); // KOSPI 1페이지 (50종목)
            prices.addAll(scrapeMarketPage(1, "KOSDAQ", 1)); // KOSDAQ 1페이지 (50종목)

            if (!prices.isEmpty()) {
                messagingTemplate.convertAndSend(TOPIC, prices);
                log.debug("실시간 시세 push: {}종목", prices.size());
            }
        } catch (Exception e) {
            log.warn("실시간 시세 push 실패: {}", e.getMessage());
        }
    }

    /** 장마감 후에도 1분마다 한 번씩 push (지연 반영용) */
    @Scheduled(fixedRate = 60_000)
    public void pushAfterHoursPrices() {
        if (connectedClients.get() == 0) return;
        if (isMarketHours()) return; // 장중엔 위 스케줄러가 처리

        try {
            List<PriceUpdateMessage> prices = new ArrayList<>();
            prices.addAll(scrapeMarketPage(0, "KOSPI", 1));
            prices.addAll(scrapeMarketPage(1, "KOSDAQ", 1));
            if (!prices.isEmpty()) {
                messagingTemplate.convertAndSend(TOPIC, prices);
            }
        } catch (Exception ignored) {}
    }

    private boolean isMarketHours() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false;
        LocalTime time = now.toLocalTime();
        return time.isAfter(LocalTime.of(8, 59)) && time.isBefore(LocalTime.of(15, 36));
    }

    private List<PriceUpdateMessage> scrapeMarketPage(int sosok, String market, int page) {
        List<PriceUpdateMessage> result = new ArrayList<>();
        try {
            String url = String.format(
                    "https://finance.naver.com/sise/sise_market_sum.nhn?sosok=%d&page=%d", sosok, page);
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .timeout(8_000)
                    .get();

            for (Element row : doc.select("table.type_2 tr")) {
                Elements cells = row.select("td");
                if (cells.size() < 10) continue;

                Element link = cells.get(1).selectFirst("a[href]");
                if (link == null) continue;

                String href = link.attr("href");
                int idx = href.indexOf("code=");
                if (idx < 0) continue;
                String code = href.substring(idx + 5).split("&")[0].trim();
                if (code.length() != 6) continue;

                String name = link.text().trim();
                long close = parseLong(cells.get(2).text());
                long change = parseChange(cells.get(3));
                BigDecimal rate = parseBD(cells.get(4).text());
                long marketCap = parseLong(cells.get(6).text());
                long volume = parseLong(cells.get(9).text());

                result.add(new PriceUpdateMessage(
                        code, name, market, close, change, rate,
                        volume, marketCap, LocalDateTime.now()));
            }
        } catch (Exception e) {
            log.debug("시세 스크래핑 실패 ({} p{}): {}", market, page, e.getMessage());
        }
        return result;
    }

    private long parseChange(Element cell) {
        String imgSrc = "";
        Element img = cell.selectFirst("img[src]");
        if (img != null) imgSrc = img.attr("src").toLowerCase();
        boolean negative = imgSrc.contains("fall") || imgSrc.contains("down")
                || cell.text().contains("▼");
        String digits = cell.text().replaceAll("[^0-9]", "");
        if (digits.isBlank()) return 0L;
        try {
            long val = Long.parseLong(digits);
            return negative ? -val : val;
        } catch (NumberFormatException e) { return 0L; }
    }

    private long parseLong(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        try { return Long.parseLong(raw.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0L; }
    }

    private BigDecimal parseBD(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return BigDecimal.ZERO;
        try { return new BigDecimal(raw.replaceAll("[^0-9.\\-]", "")); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
