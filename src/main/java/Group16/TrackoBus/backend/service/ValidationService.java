package Group16.TrackoBus.backend.service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import Group16.TrackoBus.backend.dto.request.ValidationRequestDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ValidationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RouteService routeService;
    private final TrackingService trackingService;

    public String validateDriver(String busId, ValidationRequestDto request) {

        String validationKey = "validation-state:" + busId;

        // Fetch previous state from Redis
        Map<Object, Object> previousState = redisTemplate.opsForHash().entries(validationKey);

        if (previousState.isEmpty()) {
            updateValidationState(validationKey, request.getCurrentLat(), request.getCurrentLng(), 0);
            return "OK";
        }

        double prevLat = (double) previousState.get("lat");
        double prevLng = (double) previousState.get("lng");
        int strikes = (Integer) previousState.get("strikes");

        boolean isNearRoute = routeService.isUserNearRoute(request.getRouteNumber(), request.getCurrentLng(),
                request.getCurrentLat());

        double distanceTraveled = trackingService.calculateHaversineDistance(prevLat, prevLng, request.getCurrentLat(),
                request.getCurrentLng());

        if (!isNearRoute || distanceTraveled < 2000) {
            strikes++;

            if (strikes == 3) {
                redisTemplate.delete(validationKey);
                return "KILL";
            } else {
                redisTemplate.opsForHash().put(validationKey, "lat", request.getCurrentLat());
                redisTemplate.opsForHash().put(validationKey, "lng", request.getCurrentLng());
                redisTemplate.opsForHash().put(validationKey, "strikes", strikes);
                return "PROMPT_USER";
            }
        }

        // Reset strikes and update coordinates for the next 10-minute check
        updateValidationState(validationKey, request.getCurrentLat(), request.getCurrentLng(), 0);
        return "OK";
    }

    public void updateValidationState(String key, double lat, double lng, int strikes) {
        redisTemplate.opsForHash().put(key, "lat", lat);
        redisTemplate.opsForHash().put(key, "lng", lng);
        redisTemplate.opsForHash().put(key, "strikes", strikes);
        redisTemplate.expire(key, 2, TimeUnit.HOURS);

    }

}
