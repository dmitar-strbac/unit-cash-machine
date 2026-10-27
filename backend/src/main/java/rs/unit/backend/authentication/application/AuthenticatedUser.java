package rs.unit.backend.authentication.application;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import rs.unit.backend.user.domain.UserRole;

public record AuthenticatedUser(UUID id, String displayName, UserRole role, Instant expiresAt)
        implements Serializable {}
