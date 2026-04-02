package Group16.TrackoBus.backend.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.firebase.auth.FirebaseToken;

import Group16.TrackoBus.backend.dto.request.UserRegistrationRequest;
import Group16.TrackoBus.backend.entity.Users;
import Group16.TrackoBus.backend.repository.UserRepo;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepo userRepo;

    @Transactional
    public Users registerUsers(FirebaseToken decodedToken, UserRegistrationRequest request) {
        String uid = decodedToken.getUid();

        Optional<Users> existingUser = userRepo.findById(uid);
        if (existingUser.isPresent()) {
            return existingUser.get();
        }

        Users newUsers = new Users();

        newUsers.setId(uid);
        newUsers.setEmail(decodedToken.getEmail() != null ? decodedToken.getEmail() : request.getEmail());
        newUsers.setName(request.getName());
        newUsers.setPoints(0);

        return userRepo.save(newUsers);
    }

    public Users loginUser(FirebaseToken decodedToken) {
        String uid = decodedToken.getUid();

        return userRepo.findById(uid)
                .orElseThrow(() -> new RuntimeException("User not found in database. Please register first"));
    }

}
