package Group16.TrackoBus.backend.service;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.fasterxml.jackson.databind.ObjectMapper;

import Group16.TrackoBus.backend.dto.LocationPingDto;
import Group16.TrackoBus.backend.dto.response.PointCalculateResponseDTO;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final Group16.TrackoBus.backend.service.TrackingService trackingService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final CalculatePointsService calculatePointsService;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        Principal user = event.getUser();

        if (user == null)
            return;

        String firebaseUid = user.getName();

        // Remove user From Backup queue If exists
        String backRiderBusIdKey = "backup-busId:" + firebaseUid;
        String BackBusId = (String) redisTemplate.opsForValue().get(backRiderBusIdKey);
        String queueKey = "backup-riders:" + BackBusId;
        redisTemplate.opsForZSet().remove(queueKey, firebaseUid);
        redisTemplate.delete(backRiderBusIdKey);
        System.out.println("Cleared Backup For User:" + firebaseUid);

        String driverSessionKey = "driver-bus-map:" + firebaseUid;

        // Look up which bus this driver was using
        Object rawPing = redisTemplate.opsForValue().get(driverSessionKey);

        if (rawPing != null) {

            // Safely convert the LinkedHashMap into your DTO using ObjectMapper
            LocationPingDto lastPing = objectMapper.convertValue(rawPing, LocationPingDto.class);

            String busId = lastPing.getBusId();

            System.out.println(firebaseUid + " Disconnected from Bus:" + busId);

            // FAILSAFE: Calculate Points on Unexpected Drop
            try {
                // If the frontend already calculated points, this will safely return 0
                // because the session-start key was already deleted by the HTTP endpoint.
                PointCalculateResponseDTO pointDTO = calculatePointsService.calculatePoints(firebaseUid, busId);

                if (pointDTO.getFullPoints() > 0) {
                    System.out.println("Failsafe triggered! Saved " + pointDTO.getFullPoints() + " points for user: "
                            + firebaseUid);
                }
            } catch (Exception e) {
                System.out.println("Failsafe point calculation failed for " + firebaseUid + ": " + e.getMessage());
            } finally {
                // Remove session start lat/lng data
                String startLocKey = "session-start:" + firebaseUid;
                redisTemplate.delete(startLocKey);
            }

            String backupQueueKey = "backup-riders:" + busId;

            // Pop the single oldest user from the ZSET (index 0 to 0)
            Set<Object> oldestBackupSet = redisTemplate.opsForZSet().range(backupQueueKey, 0, 0);

            if (oldestBackupSet != null && !oldestBackupSet.isEmpty()) {

                String promotedUserId = (String) oldestBackupSet.iterator().next();

                // Remove backRiderBusId for promoted user
                String promotedBackRiderBusIdKey = "backup-busId:" + promotedUserId;
                redisTemplate.delete(promotedBackRiderBusIdKey);

                System.out.println("Promoting Backup User " + promotedUserId + " for Bus: " + busId);

                // Remove them from the backup queue
                redisTemplate.opsForZSet().remove(backupQueueKey, promotedUserId);

                // Clear their backup validation state (they will start fresh as Primary)
                redisTemplate.delete("validation-state:" + busId + ":" + promotedUserId);

                // 3. Send the wake-up call via WebSocket
                messagingTemplate.convertAndSendToUser(
                        promotedUserId,
                        "/queue/promotion",
                        Map.of("action", "PROMOTE_TO_PRIMARY", "busId", busId, "routeNumber",
                                lastPing.getRouteNumber()));
            } else {
                // No valid backups left in the queue. Remove the bus.
                trackingService.killBusAndCleanUp(busId, lastPing.getRouteNumber());
            }
            // Clean up the pointer
            redisTemplate.delete(driverSessionKey);
        }
    }
}