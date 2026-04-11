package Group16.TrackoBus.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RouteSummaryResponse {
    private Long id;
    private String routeNumber;
    private String routeName;
}
