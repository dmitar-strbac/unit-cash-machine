package rs.unit.backend.user.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import rs.unit.backend.user.domain.UserRole;

public interface UserAccountRepository extends Repository<UserAccount, UUID> {

    Optional<UserAccount> findById(UUID id);

    List<UserAccount> findByRoleAndActiveTrueOrderByDisplayNameAscIdAsc(UserRole role);

    UserAccount save(UserAccount account);
}
