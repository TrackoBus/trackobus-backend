package Group16.TrackoBus.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import Group16.TrackoBus.backend.dto.response.RouteSummaryResponse;
import Group16.TrackoBus.backend.entity.Route;

@Repository
public interface RouteRepo extends JpaRepository<Route, Long> {
    Optional<Route> findByRouteNumber(String routeNumber);

    @Query("SELECT new Group16.TrackoBus.backend.dto.response.RouteSummaryResponse(r.id, r.routeNumber, r.routeName) FROM Route r")
    List<RouteSummaryResponse> findAllRouteSummaries();
}
