package Group16.TrackoBus.backend.service;

import org.springframework.stereotype.Service;

import Group16.TrackoBus.backend.entity.Users;
import Group16.TrackoBus.backend.repository.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepo userRepo;

    @Transactional
    public Users updateUserPoints(String userId, int pointsToAdd) {
        return userRepo.findById(userId).map(user -> {
            user.setPoints(user.getPoints() + pointsToAdd);
            return userRepo.save(user);
        })
                .orElseThrow(() -> new RuntimeException("User Not Found"));
    }
}