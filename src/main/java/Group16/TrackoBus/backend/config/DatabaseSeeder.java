package Group16.TrackoBus.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import Group16.TrackoBus.backend.repository.RouteRepo;
import Group16.TrackoBus.backend.service.CreateRouteService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final RouteRepo routeRepo;
    private final CreateRouteService createRouteService;

    @Override
    public void run(String... args) {

        if (routeRepo.findByRouteNumber("31").isEmpty()) {
            System.out.println("Route 31 not found. Fetching from Google Maps...");

            createRouteService.createRouteFromGoogle(
                    "31",
                    "Bandarawela - Badulla",
                    "6.830206499163397, 80.98837377845129",
                    "6.988101801532089, 81.0575736003993");
        }

        if (routeRepo.findByRouteNumber("334").isEmpty()) {
            System.out.println("Route 334 not found. Fetching from Google Maps...");

            createRouteService.createRouteFromGoogle(
                    "334",
                    "Bandarawela - Diyathalawa",
                    "6.830206499163397, 80.98837377845129",
                    "6.808266997292764, 80.95688438001588");
        }

        if (routeRepo.findByRouteNumber("99").isEmpty()) {
            System.out.println("Route 99 not found. Fetching from Google Maps...");

            createRouteService.createRouteFromGoogle(
                    "99",
                    "Badulla - Colombo",
                    "6.988101801532089, 81.0575736003993",
                    "6.93407355072448, 79.85541918001711",
                    "6.952394992063121, 80.21258210274542", // Avissawella
                    "6.859195426377098, 80.09157614325832");// Meepe
        }

        System.out.println("Routes Seeded into PostGIS Successfully!");
    }
}
