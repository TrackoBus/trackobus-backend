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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import Group16.TrackoBus.backend.dto.LocationPingDto;
import Group16.TrackoBus.backend.dto.request.ValidationRequestDto;
import Group16.TrackoBus.backend.service.OsrmService;
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

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final OsrmService osrmService;
    private final ValidationService validationService;

    @MessageMapping("/ping")
    public void receiveLocationPing(@Payload LocationPingDto ping, Principal principal) {

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
            @RequestBody ValidationRequestDto request) {

        String action = validationService.validateDriver(busId, request);

        return ResponseEntity.ok(Map.of("action", action));
    }

}
