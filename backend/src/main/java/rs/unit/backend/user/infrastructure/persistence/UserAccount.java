package rs.unit.backend.user.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import rs.unit.backend.user.domain.UserRole;

@Entity
@Table(name = "user_account", schema = "cash_machine")
public class UserAccount {

    @Id private UUID id;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "pin_hash", nullable = false, length = 100)
    private String pinHash;

    @Column(nullable = false)
    private boolean active;

    protected UserAccount() {}

    public UserAccount(UUID id, String displayName, UserRole role, String pinHash, boolean active) {
        this.id = id;
        this.displayName = displayName;
        this.role = role;
        this.pinHash = pinHash;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }

    public String getPinHash() {
        return pinHash;
    }

    public boolean isActive() {
        return active;
    }
}
