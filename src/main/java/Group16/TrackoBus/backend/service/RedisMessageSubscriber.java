package Group16.TrackoBus.backend.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import Group16.TrackoBus.backend.dto.LocationPingDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RedisMessageSubscriber {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void onMessage(String message, String channel) {
        try {
            // Redis sends raw JSON strings, so we deserialize it back to our DTO
            LocationPingDto ping = objectMapper.readValue(message, LocationPingDto.class);

            // Broadcast it via WebSockets to all subscribed mobile clients.
            // If the route is 31, it goes to /topic/route/31
            String webSocketTopic = "/topic/route/" + ping.getRouteNumber();
            messagingTemplate.convertAndSend(webSocketTopic, ping);
        } catch (Exception e) {
            System.err.println("Failed to process Redis message: " + e.getMessage());
        }
    }
}
