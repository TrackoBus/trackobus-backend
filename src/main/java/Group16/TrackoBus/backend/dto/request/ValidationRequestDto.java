package Group16.TrackoBus.backend.dto.request;

import lombok.Data;

@Data
public class ValidationRequestDto {

    private String routeNumber;
    private double currentLat;
    private double currentLng;
}
