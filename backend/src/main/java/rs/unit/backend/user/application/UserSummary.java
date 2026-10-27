package rs.unit.backend.user.application;

import java.util.UUID;
import rs.unit.backend.user.domain.UserRole;

public record UserSummary(UUID id, String displayName, UserRole role) {}
