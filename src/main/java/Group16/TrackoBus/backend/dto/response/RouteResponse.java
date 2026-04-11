package Group16.TrackoBus.backend.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RouteResponse {
    private Long id;
    private String routeNumber;
    private String routeName;
    private List<CoordinateDto> path;

    @Data
    @Builder
    public static class CoordinateDto {
        private double latitude;
        private double longitude;
    }
}
