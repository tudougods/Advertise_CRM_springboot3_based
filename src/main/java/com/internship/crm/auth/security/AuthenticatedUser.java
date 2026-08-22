package com.internship.crm.auth.security;

/** Minimal authenticated identity stored in the Spring Security context. */
public record AuthenticatedUser(Long id, String username) {
}
