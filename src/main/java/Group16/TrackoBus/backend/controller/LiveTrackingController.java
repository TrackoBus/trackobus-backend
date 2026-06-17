package Group16.TrackoBus.backend.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.data.geo.Point;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import Group16.TrackoBus.backend.dto.LocationPingDto;
import Group16.TrackoBus.backend.dto.request.ValidationRequestDto;
import Group16.TrackoBus.backend.dto.response.PointCalculateResponseDTO;
import Group16.TrackoBus.backend.service.CalculatePointsService;
import Group16.TrackoBus.backend.service.OsrmService;
import Group16.TrackoBus.backend.service.TrackingService;
import Group16.TrackoBus.backend.service.ValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/live-tracking")
public class LiveTrackingController {

    private final Group16.TrackoBus.backend.service.UserService userService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final OsrmService osrmService;
    private final ValidationService validationService;
    private final TrackingService trackingService;
    private final CalculatePointsService calculatePointsService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/ping")
    public void receiveLocationPing(@Payload LocationPingDto ping, Principal principal) {

        // Save Start Location as a simple string "lat,lng" for easy retrieval
        String startLocKey = "session-start:" + principal.getName();
        String coords = ping.getLat() + "," + ping.getLng();
        // setIfAbsent ensures we only record the VERY FIRST ping of the session
        redisTemplate.opsForValue().setIfAbsent(startLocKey, coords, 6, TimeUnit.HOURS);

        // Publish to the Pub/Sub Megaphone (For users already watching the map)
        String pubSubChannel = "live-route-" + ping.getRouteNumber();
        redisTemplate.convertAndSend(pubSubChannel, ping);

        // Save to the Current State Cache
        String cacheKey = "active-buses:" + ping.getRouteNumber();

        // Geo Key for spatial Data
        String geoKey = "geo-buses:" + ping.getRouteNumber();

        // opsForHash().put() works exactly like a Java Map/Dictionary
        redisTemplate.opsForHash().put(cacheKey, ping.getBusId(), ping);

        // Save coordinates to the Geo Set
        redisTemplate.opsForGeo().add(geoKey, new Point(ping.getLng(), ping.getLat()), ping.getBusId());

        // The "Ghost Bus" Preventer
        // Every time a ping arrives, we reset the expiration timer for this route's
        // cache to 70 Secs.
        // If a bus stops pinging entirely,
        // Redis will automatically delete the cache after 70 Secs so it vanishes
        // from the map.
        redisTemplate.expire(cacheKey, 70, TimeUnit.SECONDS);
        redisTemplate.expire(geoKey, 70, TimeUnit.SECONDS);

        // Save a pointer linking the Firebase UID to the Bus data
        String driverSessionKey = "driver-bus-map:" + principal.getName();
        redisTemplate.opsForValue().set(driverSessionKey, ping, 2, TimeUnit.MINUTES);
    }

    // --- HTTP ENDPOINT (Runs once when a commuter opens the map) ---
    @GetMapping("/routes/{routeNumber}")
    public List<LocationPingDto> getInitialBusLocations(@PathVariable String routeNumber) {

        String cacheKey = "active-buses:" + routeNumber;

        // Pull all values out of the Hash dictionary for this route
        List<Object> rawBuses = redisTemplate.opsForHash().values(cacheKey);

        // Cast them back to our DTO and return them as a JSON array
        return rawBuses.stream()
                .map(obj -> objectMapper.convertValue(obj, LocationPingDto.class))
                .collect(Collectors.toList());
    }

    @GetMapping("/routes/{routeNumber}/buses/{busId}/eta")
    public ResponseEntity<Map<String, Double>> getBusEta(@PathVariable String routeNumber, @PathVariable String busId,
            @RequestParam double lat, @RequestParam double lng) {

        String cacheKey = "active-buses:" + routeNumber;
        Object rawBus = redisTemplate.opsForHash().get(cacheKey, busId);

        if (rawBus == null)
            return ResponseEntity.notFound().build();

        LocationPingDto bus = objectMapper.convertValue(rawBus, LocationPingDto.class);

        // Fetch raw numeric data from OSRM
        Map<String, Double> routingData = osrmService.getEtaAndDistance(bus.getLat(), bus.getLng(), lat, lng);

        return ResponseEntity.ok(routingData);
    }

    @PostMapping("/buses/{busId}/validate")
    public ResponseEntity<Map<String, String>> busValidationTest(@PathVariable String busId,
            @RequestBody ValidationRequestDto request, Principal principal) {

        String userId = principal.getName();

        String action = validationService.validateDriver(busId, userId, request);

        return ResponseEntity.ok(Map.of("action", action));
    }

    @PostMapping("/buses/{busId}/report")
    public ResponseEntity<Map<String, String>> reportFakeBus(@PathVariable String busId,
            @RequestParam String routeNumber) {

        String reportKey = "reports:" + busId;

        // Increment the report count (initialize to 1 if doesn't exist)
        Long strikes = redisTemplate.opsForValue().increment(reportKey);
        redisTemplate.expire(reportKey, 2, TimeUnit.HOURS);

        if (strikes != null && strikes >= 3) {
            trackingService.killBusAndCleanUp(busId, routeNumber);
            return ResponseEntity.ok(Map.of("status", "BUS_KILLED"));
        }

        return ResponseEntity.ok(Map.of("status", "REPORT_LOGGED"));
    }

    @PostMapping("/buses/{busId}/backup")
    public ResponseEntity<Void> toggleBackupRider(@PathVariable String busId, @RequestParam boolean isOptingIn,
            Principal principal) {

        String userId = principal.getName();
        String queueKey = "backup-riders:" + busId;
        String backBusIdKey = "backup-busId:" + userId;

        if (isOptingIn) {
            // Add to the ZSET scored by the current time (FIFO Queue)
            redisTemplate.opsForZSet().add(queueKey, userId, System.currentTimeMillis());
            redisTemplate.expire(queueKey, 3, TimeUnit.HOURS);

            // Add Backup rider BusId
            redisTemplate.opsForValue().set(backBusIdKey, busId, 3, TimeUnit.HOURS);
        } else {
            // User manually opted out or closed the app
            redisTemplate.opsForZSet().remove(queueKey, userId);
            // Also clean up their validation state
            redisTemplate.delete("validation-state:" + busId + ":" + userId);
            // Remove Backup rider BusId
            redisTemplate.delete(backBusIdKey);
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/buses/{busId}/points/calculate")
    public ResponseEntity<Map<String, Object>> calculateSessionPoints(@PathVariable String busId, Principal principal) {

        String userId = principal.getName();
        String startLocKey = "session-start:" + userId;

        try {
            PointCalculateResponseDTO pointsDto = calculatePointsService.calculatePoints(userId, busId);

            if (pointsDto == null) {
                return ResponseEntity.internalServerError().build();
            }

            Integer totalPoints = pointsDto.getFullPoints();
            Double distance = pointsDto.getDistance();
            Integer distancePoints = pointsDto.getDistancePoints();
            Integer likes = pointsDto.getLikes();
            Integer likePoints = pointsDto.getLikePoints();

            return ResponseEntity.ok(Map.of("totalPoints", totalPoints, "distance", distance, "distancePoints",
                    distancePoints, "likes", likes, "likePoints", likePoints));
        } finally {
            redisTemplate.delete(startLocKey);
        }
    }

    @PostMapping("/buses/backup/addPoints")
    public ResponseEntity<Void> addBackupPoints(Principal principal) {

        try {
            userService.updateUserPoints(principal.getName(), 5);
        } catch (Exception e) {
            System.out.println("Failed to Add Backup Points: " + e.getMessage());
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/buses/{busId}/like")
    public ResponseEntity<Void> likeBus(@PathVariable String busId, Principal principal) {

        String trackerId = principal.getName();
        String likersKey = "bus-likers:" + busId;

        // Try to add the user to the Set.
        // Returns 1 if added, 0 if they were already in the Set.
        Long added = redisTemplate.opsForSet().add(likersKey, trackerId);
        redisTemplate.expire(likersKey, 6, TimeUnit.HOURS);

        Long totalLikes = redisTemplate.opsForSet().size(likersKey);

        if (added != null && added > 0) {

            // BROADCAST the new count live to anyone listening to this bus
            messagingTemplate.convertAndSend(
                    "/topic/buses/" + busId + "/likes",
                    (Object) Map.of("totalLikes", totalLikes));
        }
        return ResponseEntity.ok().build();
    }

}