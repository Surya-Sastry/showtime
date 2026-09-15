package com.showtime.identity.web.dto;

import com.showtime.identity.domain.Role;
import com.showtime.identity.domain.User;
import java.util.UUID;

public record UserResponse(UUID id, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
