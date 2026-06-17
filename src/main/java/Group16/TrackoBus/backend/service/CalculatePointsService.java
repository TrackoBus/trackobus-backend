package Group16.TrackoBus.backend.service;

import java.util.Map;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import Group16.TrackoBus.backend.dto.LocationPingDto;
import Group16.TrackoBus.backend.dto.response.PointCalculateResponseDTO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CalculatePointsService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final OsrmService osrmService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public PointCalculateResponseDTO calculatePoints(String userId, String busId) {

        String startLocKey = "session-start:" + userId;
        String reportKey = "reports:" + busId;
        String driverSessionKey = "driver-bus-map:" + userId;

        // Look up which bus this driver was using
        Object rawPing = redisTemplate.opsForValue().get(driverSessionKey);

        // Safely convert the LinkedHashMap into your DTO using ObjectMapper
        LocationPingDto lastPing = rawPing != null ? objectMapper.convertValue(rawPing, LocationPingDto.class) : null;

        if (lastPing != null) {
            // 1. The Fraud Check
            // Retrieve the strike count. Note: Redis template often returns Integers as
            // Numbers.
            Number strikes = (Number) redisTemplate.opsForValue().get(reportKey);
            if (strikes != null && strikes.intValue() >= 3) {
                System.out.println("No points awarded to " + userId + ". Fraud threshold reached.");
                return new PointCalculateResponseDTO(0, 0, 0.0, 0, 0);
            }

            // 2. Fetch Strat Coords
            String startLoc = (String) redisTemplate.opsForValue().get(startLocKey);
            if (startLoc == null) {
                return new PointCalculateResponseDTO(0, 0, 0.0, 0, 0);
            }

            String[] parts = startLoc.split(",");
            double startLat = Double.parseDouble(parts[0]);
            double startLng = Double.parseDouble(parts[1]);

            // 3. Call OSRM for the exact driven distance
            Map<String, Double> routingData = osrmService.getEtaAndDistance(startLat, startLng, lastPing.getLat(),
                    lastPing.getLng());

            double distanceMeters = routingData.get("distanceMeters");

            if (distanceMeters <= 0) {
                return new PointCalculateResponseDTO(0, 0, 0.0, 0, 0);
            }

            // 4. Calculate distance Points
            // 1 point per 100 meters (10 points per km)
            int distancePoints = (int) Math.floor(distanceMeters / 100.0);

            // 5. Calculate Like Points
            // 5 points per Like
            String likerKey = "bus-likers:" + busId;
            Long totalLikes = redisTemplate.opsForSet().size(likerKey);
            int likeCount = totalLikes.intValue();

            int likePoints = 0;
            if (totalLikes != null && totalLikes > 0) {
                likePoints = likeCount * 5;
            }

            int finalPoints = distancePoints + likePoints;

            // 5. Save points to the Database via UserService
            userService.updateUserPoints(userId, finalPoints);

            redisTemplate.delete(likerKey);

            return new PointCalculateResponseDTO(finalPoints, distancePoints, distanceMeters, likePoints,
                    likeCount);
        } else {
            return null;
        }

    }

}