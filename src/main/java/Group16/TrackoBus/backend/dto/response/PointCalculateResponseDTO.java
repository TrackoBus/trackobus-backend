package Group16.TrackoBus.backend.dto.response;

import lombok.Data;

@Data
public class PointCalculateResponseDTO {
    private int fullPoints;
    private int distancePoints;
    private double distance;
    private int likePoints;
    private int likes;

    public PointCalculateResponseDTO(int fullPoints, int distancePoints, double distance, int likePoints, int likes) {
        this.fullPoints = fullPoints;
        this.distancePoints = distancePoints;
        this.distance = distance;
        this.likePoints = likePoints;
        this.likes = likes;
    }
}