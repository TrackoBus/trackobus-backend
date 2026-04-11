package Group16.TrackoBus.backend.utils;

import java.util.List;

import com.google.maps.internal.PolylineEncoding;
import com.google.maps.model.LatLng;

public class PolylineUtils {

    public static List<LatLng> decode(String encodedPolyline) {
        return PolylineEncoding.decode(encodedPolyline);
    }
}
