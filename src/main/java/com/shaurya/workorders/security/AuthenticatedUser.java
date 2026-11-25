package com.shaurya.workorders.security;

import com.shaurya.workorders.user.Role;

/** The principal placed in the SecurityContext. Built from JWT claims only, so no DB hit per request. */
public record AuthenticatedUser(Long id, String username, Role role) {
}
