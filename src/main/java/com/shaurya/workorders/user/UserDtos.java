package com.shaurya.workorders.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role) {
    }

    public record UserResponse(Long id, String username, Role role) {
        public static UserResponse from(AppUser u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getRole());
        }
    }
}
