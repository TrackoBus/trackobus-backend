package Group16.TrackoBus.backend.controller;

import Group16.TrackoBus.backend.dto.request.CreateRouteRequest;
import Group16.TrackoBus.backend.service.CreateRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/routes")
@RequiredArgsConstructor
public class RouteAdminController {

    private final CreateRouteService createRouteService;

    @PostMapping
    public ResponseEntity<String> createNewRoute(@RequestBody CreateRouteRequest request) {

        // Convert the waypoints List into a standard String array for the service layer
        String[] waypointsArray = request.getWaypoints() != null
                ? request.getWaypoints().toArray(new String[0])
                : new String[0];

        // Pass the fields from your DTO straight into the existing service method
        createRouteService.createRouteFromGoogle(
                request.getRouteNumber(),
                request.getRouteName(),
                request.getOrigin(),
                request.getDestination(),
                waypointsArray);

        return ResponseEntity.ok("Route created and processed via Google Directions API successfully!");
    }
}