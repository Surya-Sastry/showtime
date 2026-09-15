package com.showtime.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Intentionally has no {@code role} field: role is never accepted from the
 * client (mass-assignment prevention). Every self-registered account is
 * {@code CUSTOMER}; manager accounts are provisioned out-of-band.
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 12, max = 200) String password) {
}
