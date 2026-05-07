package Group16.TrackoBus.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Distance;
import org.springframework.data.redis.connection.RedisGeoCommands.DistanceUnit;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Group16.TrackoBus.backend.dto.response.RouteResponse;
import Group16.TrackoBus.backend.dto.response.RouteSummaryResponse;
import Group16.TrackoBus.backend.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/{routeNumber}")
    public ResponseEntity<RouteResponse> getRoute(@PathVariable String routeNumber) {
        return ResponseEntity.ok(routeService.getRoute(routeNumber));
    }

    @GetMapping
    public ResponseEntity<List<RouteSummaryResponse>> getAllRoutes() {
        return ResponseEntity.ok(routeService.getAllRoutes());
    }

    @GetMapping("/proxCheck")
    public ResponseEntity<Boolean> getProxCheck(@RequestParam String routeNumber, @RequestParam double longitude,
            @RequestParam double latitude) {
        return ResponseEntity.ok(routeService.isUserNearRoute(routeNumber, longitude, latitude));
    }

    @GetMapping("/{routeNumber}/closest")
    public ResponseEntity<Map<String, String>> getClosestBus(@PathVariable String routeNumber, @RequestParam double lat,
            @RequestParam double lng) {
        String geokey = "geo-buses:" + routeNumber;

        // Search for buses within a 20-kilometer radius of the commuter
        // Sort them ascending (closest first) and limit the result to exactly 1
        GeoResults<GeoLocation<Object>> results = redisTemplate.opsForGeo().search(geokey,
                GeoReference.fromCoordinate(lng, lat), new Distance(20, DistanceUnit.KILOMETERS),
                GeoSearchCommandArgs.newGeoSearchArgs().sortAscending().limit(1));

        // If no buses are found within the radius (or route is empty)
        if (results == null || results.getContent().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Extract the busId of the closest match
        String closestBusId = (String) results.getContent().get(0).getContent().getName();

        return ResponseEntity.ok(Map.of("busId", closestBusId));
    }

}
