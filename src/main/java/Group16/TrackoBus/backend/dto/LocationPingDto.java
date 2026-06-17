package Group16.TrackoBus.backend.dto;

import lombok.Data;

@Data
public class LocationPingDto {
    private String routeNumber;
    private String busId;
    private double lat;
    private double lng;
    private long timestamp;
    private boolean primary;
    private boolean offline;
}