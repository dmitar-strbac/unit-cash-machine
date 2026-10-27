package rs.unit.backend.user.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.unit.backend.user.domain.UserRole;
import rs.unit.backend.user.infrastructure.persistence.UserAccountRepository;

@Service
public class UserDirectoryService {

    private final UserAccountRepository repository;

    public UserDirectoryService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> findSelectableUsers(UserRole role) {
        if (role == UserRole.CIT) {
            return List.of();
        }

        return repository.findByRoleAndActiveTrueOrderByDisplayNameAscIdAsc(role).stream()
                .map(
                        account ->
                                new UserSummary(
                                        account.getId(),
                                        account.getDisplayName(),
                                        account.getRole()))
                .toList();
    }
}
