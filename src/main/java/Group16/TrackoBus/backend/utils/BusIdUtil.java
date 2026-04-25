package Group16.TrackoBus.backend.utils;

import java.util.UUID;

public class BusIdUtil {
    public static String generateUniqueBusId() {
        return "BID" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
