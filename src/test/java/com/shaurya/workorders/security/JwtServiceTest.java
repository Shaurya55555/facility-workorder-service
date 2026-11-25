package com.shaurya.workorders.security;

import com.shaurya.workorders.user.AppUser;
import com.shaurya.workorders.user.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes-long";
    static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    AppUser tech = userWithId(7L, "tech1", Role.TECHNICIAN);

    @Test
    void roundTripPreservesIdentityAndRole() {
        JwtService jwt = new JwtService(SECRET, Duration.ofMinutes(60), fixed(NOW));

        AuthenticatedUser parsed = jwt.parse(jwt.issue(tech));

        assertThat(parsed).isEqualTo(new AuthenticatedUser(7L, "tech1", Role.TECHNICIAN));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = new JwtService(SECRET, Duration.ofMinutes(60), fixed(NOW)).issue(tech);
        JwtService later = new JwtService(SECRET, Duration.ofMinutes(60), fixed(NOW.plus(Duration.ofMinutes(61))));

        assertThatThrownBy(() -> later.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenIsStillValidJustBeforeExpiry() {
        String token = new JwtService(SECRET, Duration.ofMinutes(60), fixed(NOW)).issue(tech);
        JwtService almost = new JwtService(SECRET, Duration.ofMinutes(60), fixed(NOW.plus(Duration.ofMinutes(59))));

        assertThat(almost.parse(token).username()).isEqualTo("tech1");
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String forged = new JwtService("another-secret-that-is-also-32-bytes-long!!", Duration.ofMinutes(60), fixed(NOW)).issue(tech);
        JwtService jwt = new JwtService(SECRET, Duration.ofMinutes(60), fixed(NOW));

        assertThatThrownBy(() -> jwt.parse(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtService jwt = new JwtService(SECRET, Duration.ofMinutes(60), fixed(NOW));
        String[] parts = jwt.issue(tech).split("\\.");
        // swap in a payload that claims ADMIN but keep the original signature
        String evilPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"tech1\",\"uid\":7,\"role\":\"ADMIN\",\"exp\":4102444800}").getBytes());
        String tampered = parts[0] + "." + evilPayload + "." + parts[2];

        assertThatThrownBy(() -> jwt.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void garbageIsRejected() {
        JwtService jwt = new JwtService(SECRET, Duration.ofMinutes(60), fixed(NOW));

        assertThatThrownBy(() -> jwt.parse("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> new JwtService("too-short", Duration.ofMinutes(1), fixed(NOW)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Clock fixed(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static AppUser userWithId(Long id, String name, Role role) {
        AppUser u = new AppUser(name, "hash", role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }
}
