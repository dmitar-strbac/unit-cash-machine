package rs.unit.backend.authentication.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import rs.unit.backend.user.domain.UserRole;

public record AuthenticatedUser(
        UUID id,
        String displayName,
        UserRole role,
        Instant expiresAt,
        @JsonIgnore UUID loginId,
        @JsonIgnore long applicationGeneration)
        implements Serializable {

    public AuthenticatedUser(UUID id, String displayName, UserRole role, Instant expiresAt) {
        this(id, displayName, role, expiresAt, UUID.randomUUID(), 0);
    }

    public AuthenticatedUser(
            UUID id, String displayName, UserRole role, Instant expiresAt, UUID loginId) {
        this(id, displayName, role, expiresAt, loginId, 0);
    }
}
