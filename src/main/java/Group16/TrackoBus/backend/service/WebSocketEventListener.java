package Group16.TrackoBus.backend.service;

import java.security.Principal;
import java.util.LinkedHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.fasterxml.jackson.databind.ObjectMapper;

import Group16.TrackoBus.backend.dto.LocationPingDto;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        Principal user = event.getUser();

        if (user == null)
            return;

        String firebaseUid = user.getName();
        String driverSessionKey = "driver-bus-map:" + firebaseUid;

        // Look up which bus this driver was using
        Object rawPing = redisTemplate.opsForValue().get(driverSessionKey);

        if (rawPing != null) {

            // Safely convert the LinkedHashMap into your DTO using ObjectMapper
            LocationPingDto lastPing = objectMapper.convertValue(rawPing, LocationPingDto.class);

            System.out.println(firebaseUid + " Disconnected. Removing Bus: " + lastPing.getBusId());

            // Immediately delete the bus from the Active Route Cache
            String activeBusesCacheKey = "active-buses:" + lastPing.getRouteNumber();
            redisTemplate.opsForHash().delete(activeBusesCacheKey, lastPing.getBusId());

            // Flip the offline flag and broadcast the "Kill Signal"
            lastPing.setOffline(true);
            String pubSubChannel = "live-route-" + lastPing.getRouteNumber();
            redisTemplate.convertAndSend(pubSubChannel, lastPing);

            // Clean up the pointer
            redisTemplate.delete(driverSessionKey);
        }
    }
}
