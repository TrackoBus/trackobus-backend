package Group16.TrackoBus.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

import com.google.maps.DirectionsApi;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsStep;
import com.google.maps.model.TravelMode;

import Group16.TrackoBus.backend.entity.Route;
import Group16.TrackoBus.backend.repository.RouteRepo;
import Group16.TrackoBus.backend.utils.PolylineUtils;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreateRouteService {

    private final RouteRepo routeRepo;
    private final GeoApiContext geoApiContext;

    public void createRouteFromGoogle(String routeNum, String routeName, String origin, String destination,
            String... waypoints) {
        try {
            DirectionsApiRequest request = DirectionsApi.getDirections(geoApiContext, origin, destination)
                    .mode(TravelMode.DRIVING);

            if (waypoints != null && waypoints.length > 0) {
                request.waypoints(waypoints);
            }

            DirectionsResult result = request.await();

            if (result.routes == null || result.routes.length == 0) {
                throw new RuntimeException("Google could not find a route for: " + routeNum);
            }

            List<com.google.maps.model.LatLng> decodedPath = new ArrayList<>();

            // Iterate through all legs (routes with waypoints have multiple legs)
            for (DirectionsLeg leg : result.routes[0].legs) {
                // Iterate through all steps in each leg
                for (DirectionsStep step : leg.steps) {
                    // Decode the high-resolution polyline for this specific step
                    List<com.google.maps.model.LatLng> stepPoints = PolylineUtils
                            .decode(step.polyline.getEncodedPath());
                    decodedPath.addAll(stepPoints);
                }
            }

            // Convert to JTS Coordinates (X, Y) -> (Long, Lat)
            Coordinate[] coords = decodedPath.stream().map(p -> new Coordinate(p.lng, p.lat))
                    .toArray(Coordinate[]::new);

            GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
            LineString lineString = factory.createLineString(coords);

            Route route = new Route();
            route.setRouteNumber(routeNum);
            route.setRouteName(routeName);
            route.setPathData(lineString);

            routeRepo.save(route);
            System.out.println("Successfully fetched and saved Route " + routeNum + " from Google Maps.");

        } catch (Exception e) {
            System.err.println("Failed to create route " + routeNum + ": " + e.getMessage());
        }
    }
}
