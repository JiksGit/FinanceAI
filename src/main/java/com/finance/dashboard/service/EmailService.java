package com.finance.dashboard.service;

import com.finance.dashboard.config.SignalConfig;
import com.finance.dashboard.entity.StockSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final SignalConfig signalConfig;

    public EmailService(Optional<JavaMailSender> mailSender, SignalConfig signalConfig) {
        this.mailSender = mailSender.orElse(null);
        this.signalConfig = signalConfig;
    }

    public void sendSignalAlert(String toEmail, StockSignal signal) {
        if (!signalConfig.mailEnabled()) {
            log.info("[메일 비활성화] {} 시그널 알림을 {}에게 보내지 않음", signal.getStockSymbol(), toEmail);
            return;
        }

        String typeLabel = signal.getSignalType() == StockSignal.SignalType.BUY ? "매수" : "매도";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[AI Finance] %s %s 시그널 발생".formatted(signal.getStockSymbol(), typeLabel));
        message.setText("""
                관심 종목 %s에 %s 시그널이 발생했습니다.

                지표: %s
                기준일: %s

                %s
                """.formatted(
                signal.getStockSymbol(), typeLabel, signal.getIndicator(),
                signal.getSignalDate(), signal.getAiExplanation()
        ));

        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("시그널 알림 메일 발송 실패: {} -> {}", signal.getStockSymbol(), toEmail, e);
        }
    }
}
