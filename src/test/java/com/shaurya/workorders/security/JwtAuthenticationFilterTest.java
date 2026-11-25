package com.shaurya.workorders.security;

import com.shaurya.workorders.user.AppUser;
import com.shaurya.workorders.user.Role;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    JwtService jwt = new JwtService("unit-test-secret-that-is-at-least-32-bytes-long",
            Duration.ofMinutes(60), Clock.systemUTC());
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerTokenAuthenticatesTheRequestWithRoleAuthority() throws Exception {
        AppUser manager = new AppUser("manager1", "hash", Role.MANAGER);
        ReflectionTestUtils.setField(manager, "id", 4L);

        FilterChain chain = run("Bearer " + jwt.issue(manager));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(new AuthenticatedUser(4L, "manager1", Role.MANAGER));
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MANAGER");
        assertThat(((MockFilterChain) chain).getRequest()).isNotNull();   // chain continued
    }

    @Test
    void missingHeaderLeavesRequestAnonymousButContinuesTheChain() throws Exception {
        FilterChain chain = run(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(((MockFilterChain) chain).getRequest()).isNotNull();
    }

    @Test
    void invalidTokenLeavesRequestAnonymous() throws Exception {
        run("Bearer definitely.not.valid");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nonBearerSchemeIsIgnored() throws Exception {
        run("Basic dXNlcjpwYXNz");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private FilterChain run(String authorization) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/work-orders");
        if (authorization != null) {
            req.addHeader("Authorization", authorization);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        return chain;
    }
}
