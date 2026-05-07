package Group16.TrackoBus.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import Group16.TrackoBus.backend.dto.response.RouteSummaryResponse;
import Group16.TrackoBus.backend.entity.Route;

@Repository
public interface RouteRepo extends JpaRepository<Route, Long> {
    Optional<Route> findByRouteNumber(String routeNumber);

    @Query("SELECT new Group16.TrackoBus.backend.dto.response.RouteSummaryResponse(r.id, r.routeNumber, r.routeName) FROM Route r")
    List<RouteSummaryResponse> findAllRouteSummaries();

    @Query(value = """
            SELECT COUNT(r.route_id) > 0
            FROM routes r
            WHERE r.route_number = :routeNumber
            AND ST_DWithin(
                r.path_data,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                0.00045
            )
            """, nativeQuery = true)
    boolean isUserNearRoute(
            @Param("routeNumber") String routeNumber,
            @Param("longitude") double longitude,
            @Param("latitude") double latitude);
}
