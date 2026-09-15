package com.showtime.identity.service;

import com.showtime.identity.domain.Role;
import com.showtime.identity.domain.User;
import com.showtime.identity.exception.EmailAlreadyRegisteredException;
import com.showtime.identity.exception.InvalidCredentialsException;
import com.showtime.identity.repository.UserRepository;
import com.showtime.identity.security.JwtService;
import com.showtime.identity.security.PasswordHasher;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /**
     * A hash of a random, never-issued password. Used to run the same Argon2id
     * verification work for unknown emails as for known ones, so a login
     * attempt does not leak "this email doesn't exist" through response
     * timing. Computed once at startup, not per request.
     */
    private final String decoyHash;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.decoyHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    @Transactional
    public User register(String email, String rawPassword) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = User.register(email, passwordHasher.hash(rawPassword), Role.CUSTOMER);
        return userRepository.save(user);
    }

    public IssuedToken login(String email, String rawPassword) {
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        String hashToCheck = (user != null) ? user.getPasswordHash() : decoyHash;
        boolean matches = passwordHasher.matches(rawPassword, hashToCheck);

        if (user == null || !matches) {
            throw new InvalidCredentialsException();
        }
        String token = jwtService.issueAccessToken(user);
        return new IssuedToken(token, user);
    }

    public record IssuedToken(String accessToken, User user) {
    }
}
