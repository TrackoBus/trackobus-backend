package Group16.TrackoBus.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.maps.GeoApiContext;

import jakarta.annotation.PreDestroy;

@Configuration
public class GoogleMapsConfig {

    @Value("${google.maps.api-key}")
    private String apiKey;

    private GeoApiContext context;

    @Bean
    public GeoApiContext geoApiContext() {
        context = new GeoApiContext.Builder()
                .apiKey(apiKey)
                .build();
        return context;
    }

    // Gracefully shuts down Google's background network threads when the server
    // stops
    @PreDestroy
    public void cleanup() {
        if (context != null) {
            context.shutdown();
        }
    }
}
