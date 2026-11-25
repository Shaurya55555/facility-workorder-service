package com.shaurya.workorders.user;

import com.shaurya.workorders.error.ConflictException;
import com.shaurya.workorders.user.UserDtos.CreateUserRequest;
import com.shaurya.workorders.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new ConflictException("Username already taken: " + req.username());
        }
        AppUser saved = users.save(new AppUser(req.username(), encoder.encode(req.password()), req.role()));
        return UserResponse.from(saved);
    }

    /** Managers need this to pick a technician to assign. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional(readOnly = true)
    public List<UserResponse> list(@RequestParam(required = false) Role role) {
        List<AppUser> found = role == null ? users.findAll() : users.findByRoleOrderByUsername(role);
        return found.stream().map(UserResponse::from).toList();
    }
}
