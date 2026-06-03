package Group16.TrackoBus.backend.service;

import org.springframework.stereotype.Service;

@Service
public class TrackingService {

    public double calculateHaversineDistance(double lat1, double lng1, double lat2, double lng2) {

        // Earth's mean radius in meters
        final double R = 6371000.0;

        // Convert degrees to radians
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // Return the final distance in meters
        return R * c;
    }
}
