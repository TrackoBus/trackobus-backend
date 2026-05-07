package Group16.TrackoBus.backend.controller;

import java.util.List;

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

}
