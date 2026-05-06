package Group16.TrackoBus.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.google.firebase.auth.FirebaseAuthException;

import Group16.TrackoBus.backend.service.FirebaseAuthService;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final FirebaseAuthService firebaseAuthService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Create a scheduler to handle the heartbeats
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("ws-heartbeat-thread-");
        taskScheduler.initialize();

        registry.enableSimpleBroker("/topic")
                .setTaskScheduler(taskScheduler)
                // Server Sends Heartbeat Every 60s, Server Expects Client Heartbeat Every 60s
                .setHeartbeatValue(new long[] { 60000, 60000 });

        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-live-tracking").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())
                        || StompCommand.STOMP.equals(accessor.getCommand())) {
                    // Extract the token from the STOMP header
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);

                        try {
                            // Validate the token and attach the user to the WebSocket session
                            Authentication user = firebaseAuthService.verifyToken(token);
                            accessor.setUser(user);
                            System.out.println("[WebSocket] User connected: " + user.getName());

                            // Explicitly build a NEW message with the authenticated user attached
                            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
                        } catch (FirebaseAuthException e) {
                            System.err.println("WebSocket Firebase Auth Failed: " + e.getMessage());
                            // Reject the connection entirely if auth fails!
                            throw new AccessDeniedException("Invalid Firebase Token");
                        }
                    } else {
                        // Reject if they try to connect without a token
                        throw new AccessDeniedException("Missing Authorization Header");
                    }
                }

                if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    String username = accessor.getUser() != null ? accessor.getUser().getName() : "anonymous";
                    System.out.println("[WebSocket] User disconnected: " + username);
                }

                return message;
            }
        });
    }
}
