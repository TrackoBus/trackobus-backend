package Group16.TrackoBus.backend.dto;

import lombok.Data;

@Data
public class LocationPingDto {
    private String routeNumber;
    private String busId;
    private double lat;
    private double lng;
    private long timestamp;
    private boolean primary; // true for 3-sec pings, false for 60-sec backup pings
    private boolean offline;
}
