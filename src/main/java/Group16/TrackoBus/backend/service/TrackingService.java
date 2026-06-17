package Group16.TrackoBus.backend.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import Group16.TrackoBus.backend.dto.LocationPingDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TrackingService {

    private final RedisTemplate<String, Object> redisTemplate;

    public double calculateHaversineDistance(double lat1, double lng1, double lat2, double lng2) {

        // Earth's mean radius in meters
        final double R = 6371000.0;

        // Convert degrees to radians
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // Return the final distance in meters
        return R * c;
    }

    public void killBusAndCleanUp(String busId, String routeNumber) {
        System.out.println("Executing Kill Switch for Bus: " + busId);

        // Delete from Active Hash & Geo Set
        redisTemplate.opsForHash().delete("active-buses:" + routeNumber, busId);
        redisTemplate.opsForZSet().remove("geo-buses:" + routeNumber, busId);

        // Broadcast the offline Signal
        LocationPingDto killSignal = new LocationPingDto();
        killSignal.setBusId(busId);
        killSignal.setRouteNumber(routeNumber);
        killSignal.setOffline(true);
        redisTemplate.convertAndSend("live-route-" + routeNumber, killSignal);

        // Clean up auxiliary data
        redisTemplate.delete("backup-riders:" + busId);
        redisTemplate.delete("reports:" + busId);
    }
}