package se.vestige_be.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import lombok.AllArgsConstructor;

@Configuration
@EnableWebSocket
@AllArgsConstructor
public class SimpleWebSocketConfig implements WebSocketConfigurer {

    private final SimpleChatHandlerConfig simpleChatHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(simpleChatHandler, "/chat")
                .setAllowedOriginPatterns("*")
                .setAllowedOrigins(
                        "https://vestigehouse.vercel.app",
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                        "http://localhost:3001"
                );
    }
}
