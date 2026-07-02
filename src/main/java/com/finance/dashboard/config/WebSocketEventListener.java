package com.finance.dashboard.config;

import com.finance.dashboard.service.PriceUpdateScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PriceUpdateScheduler priceUpdateScheduler;

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        priceUpdateScheduler.clientConnected();
        log.debug("WebSocket 클라이언트 연결");
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        priceUpdateScheduler.clientDisconnected();
        log.debug("WebSocket 클라이언트 해제");
    }
}
