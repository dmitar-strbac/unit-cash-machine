package rs.unit.backend.user.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rs.unit.backend.user.application.UserDirectoryService;
import rs.unit.backend.user.application.UserSummary;
import rs.unit.backend.user.domain.UserRole;

@RestController
@RequestMapping("/api/users")
public class UserDirectoryController {

    private final UserDirectoryService directoryService;

    public UserDirectoryController(UserDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping
    public List<UserSummary> findSelectableUsers(@RequestParam("role") UserRole role) {
        return directoryService.findSelectableUsers(role);
    }
}
