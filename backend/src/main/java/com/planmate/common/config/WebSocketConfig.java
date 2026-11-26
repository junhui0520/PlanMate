package com.planmate.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 연결 주소 설정 (COL-06-01 변경 전파). STOMP·SockJS는 쓰지 않는다.
 * 허용 origin은 프론트 주소(planmate.frontend-url) 하나.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final String frontendUrl;

    public WebSocketConfig(@Value("${planmate.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // TODO(B에 요청): TripSyncHandler 연결 주소 등록 (COL-06-01) — registry.addHandler(tripSyncHandler, "<B가 정할 주소>").setAllowedOrigins(frontendUrl)
    }
}
