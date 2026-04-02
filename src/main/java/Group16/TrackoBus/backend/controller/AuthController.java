package Group16.TrackoBus.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.firebase.auth.FirebaseToken;

import Group16.TrackoBus.backend.dto.request.UserRegistrationRequest;
import Group16.TrackoBus.backend.entity.Users;
import Group16.TrackoBus.backend.service.AuthService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Users> register(@RequestAttribute("decodedToken") FirebaseToken decodedToken,
            @RequestBody UserRegistrationRequest request) {

        Users savedUsers = authService.registerUsers(decodedToken, request);
        return new ResponseEntity<>(savedUsers, org.springframework.http.HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<Users> login(@RequestAttribute("decodedToken") FirebaseToken decodedToken) {
        Users users = authService.loginUser(decodedToken);
        return new ResponseEntity<>(users, HttpStatus.OK);
    }
}
