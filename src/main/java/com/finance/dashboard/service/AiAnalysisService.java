package com.finance.dashboard.service;

import com.finance.dashboard.dto.request.AiAnalyzeRequest;
import com.finance.dashboard.dto.response.AiAnalyzeResponse;
import com.finance.dashboard.dto.response.ChatHistoryResponse;
import com.finance.dashboard.dto.response.ExchangeRateResponse;
import com.finance.dashboard.dto.response.FavoriteStockResponse;
import com.finance.dashboard.entity.ChatHistory;
import com.finance.dashboard.exception.CustomException;
import com.finance.dashboard.exception.ErrorCode;
import com.finance.dashboard.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final ExchangeRateService exchangeRateService;
    private final StockService stockService;
    private final OpenAiService openAiService;
    private final ChatHistoryRepository chatHistoryRepository;

    @Transactional
    public AiAnalyzeResponse analyze(Long userId, AiAnalyzeRequest request) {
        if (!openAiService.isEnabled()) {
            throw new CustomException(ErrorCode.AI_API_ERROR);
        }

        StringBuilder context = new StringBuilder();
        List<String> contextUsed = new ArrayList<>();

        if (request.includeExchangeRate()) {
            ExchangeRateResponse rates = exchangeRateService.getTodayRates();
            context.append("=== 오늘의 환율 데이터 (기준일: ").append(rates.baseDate()).append(") ===\n");
            for (ExchangeRateResponse.RateItem rate : rates.rates()) {
                String line = "%s: %s원".formatted(rate.currency(), rate.rate());
                context.append(line).append("\n");
                contextUsed.add(line);
            }
        }

        if (request.includeStock()) {
            List<FavoriteStockResponse> favorites = stockService.getFavorites(userId);
            if (!favorites.isEmpty()) {
                context.append("=== 관심 종목 시세 ===\n");
                for (FavoriteStockResponse fav : favorites) {
                    String line = "%s: $%s".formatted(fav.stockSymbol(), fav.currentPrice());
                    context.append(line).append("\n");
                    contextUsed.add(line);
                }
            }
        }

        String systemPrompt = """
                당신은 금융 시장 분석 전문가입니다.
                아래 실시간 데이터를 바탕으로 사용자 질문에 답변해주세요.
                데이터가 없는 부분은 추측하지 말고 모른다고 답하세요.
                투자 추천이 아니라 정보 해설 관점으로 답변하세요.
                """;

        String userPrompt = """
                %s

                사용자 질문: %s
                """.formatted(context, request.question());

        String answer = openAiService.chat(systemPrompt, userPrompt);
        if (answer == null || answer.isBlank()) {
            throw new CustomException(ErrorCode.AI_API_ERROR);
        }

        ChatHistory saved = chatHistoryRepository.save(ChatHistory.builder()
                .userId(userId)
                .question(request.question())
                .answer(answer)
                .context(context.toString())
                .build());

        return new AiAnalyzeResponse(answer, contextUsed, saved.getCreatedAt());
    }

    public List<ChatHistoryResponse> getHistory(Long userId) {
        return chatHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(h -> new ChatHistoryResponse(h.getId(), h.getQuestion(), h.getAnswer(), h.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void clearHistory(Long userId) {
        chatHistoryRepository.deleteByUserId(userId);
    }
}
