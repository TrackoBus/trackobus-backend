package Group16.TrackoBus.backend.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OsrmService {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Default to localhost for now, update Azure URL later in application.yml
    @Value("${osrm.server.url:http://localhost:5000}")
    private String osrmBaseUrl;

    public Map<String, Double> getEtaAndDistance(double startLat, double startLng, double endLat, double endLng) {

        // OSRM Format: {longitude},{latitude};{longitude},{latitude}
        String url = String.format("%s/route/v1/driving/%f,%f;%f,%f?overview=false", osrmBaseUrl, startLng, startLat,
                endLng, endLat);

        try {
            ResponseEntity<String> resposne = restTemplate.getForEntity(url, String.class);
            JsonNode rootNode = objectMapper.readTree(resposne.getBody());

            // Check if OSRM found a valid route
            if (rootNode.path("code").asText().equals("Ok")) {
                JsonNode route = rootNode.path("routes").get(0);

                // OSRM returns duration in SECONDS and distance in METERS
                double durationSeconds = route.path("duration").asDouble();
                double distanceMeters = route.path("distance").asDouble();

                return Map.of("etaSeconds", durationSeconds, "distanceMeters", distanceMeters);
            }
        } catch (Exception e) {
            System.err.println("OSRM Routing failed: " + e.getMessage());
        }

        // Return standard error values (-1.0) so the frontend knows routing failed
        return Map.of("etaSeconds", -1.0, "distanceMeters", -1.0);
    }

}