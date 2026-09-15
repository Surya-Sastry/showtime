package com.showtime.identity.web;

import com.showtime.identity.domain.User;
import com.showtime.identity.repository.UserRepository;
import com.showtime.identity.security.JwtService.JwtPrincipal;
import com.showtime.identity.service.AuthService;
import com.showtime.identity.web.dto.AuthResponse;
import com.showtime.identity.web.dto.LoginRequest;
import com.showtime.identity.web.dto.RegisterRequest;
import com.showtime.identity.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.IssuedToken issued = authService.login(request.email(), request.password());
        return AuthResponse.bearer(issued.accessToken(), 15 * 60);
    }

    @GetMapping("/auth/me")
    public UserResponse me(@AuthenticationPrincipal JwtPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new IllegalStateException("authenticated user no longer exists"));
        return UserResponse.from(user);
    }
}
