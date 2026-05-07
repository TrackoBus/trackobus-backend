package Group16.TrackoBus.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Service;

import Group16.TrackoBus.backend.dto.response.RouteResponse;
import Group16.TrackoBus.backend.dto.response.RouteSummaryResponse;
import Group16.TrackoBus.backend.entity.Route;
import Group16.TrackoBus.backend.repository.RouteRepo;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepo routeRepo;

    public RouteResponse getRoute(String routeNumber) {
        Route route = routeRepo.findByRouteNumber(routeNumber)
                .orElseThrow(() -> new RuntimeException("Route not found"));

        List<RouteResponse.CoordinateDto> pathDto = new ArrayList<>();

        for (Coordinate coord : route.getPathData().getCoordinates()) {
            // JTS stores coordinates as (x, y) which translates to (Longitude, Latitude)
            pathDto.add(RouteResponse.CoordinateDto.builder()
                    .latitude(coord.y)
                    .longitude(coord.x)
                    .build());
        }

        RouteResponse response = RouteResponse.builder()
                .id(route.getId())
                .routeNumber(route.getRouteNumber())
                .routeName(route.getRouteName())
                .path(pathDto)
                .build();

        return response;
    }

    public List<RouteSummaryResponse> getAllRoutes() {
        List<RouteSummaryResponse> summaries = routeRepo.findAllRouteSummaries();
        return summaries;
    }

    public boolean isUserNearRoute(String routeNumber, double longitude, double latitude) {
        return routeRepo.isUserNearRoute(routeNumber, longitude, latitude);
    }
}
