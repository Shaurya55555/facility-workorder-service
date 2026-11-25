package com.shaurya.workorders.auth;

import com.shaurya.workorders.auth.AuthDtos.LoginRequest;
import com.shaurya.workorders.auth.AuthDtos.TokenResponse;
import com.shaurya.workorders.security.JwtService;
import com.shaurya.workorders.user.AppUser;
import com.shaurya.workorders.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        // Same error for "no such user" and "wrong password" so usernames cannot be enumerated.
        AppUser user = users.findByUsername(req.username())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        return new TokenResponse(jwt.issue(user), "Bearer", jwt.ttlSeconds(), user.getRole());
    }
}
