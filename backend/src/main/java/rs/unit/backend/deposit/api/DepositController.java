package rs.unit.backend.deposit.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.deposit.application.BanknoteResult;
import rs.unit.backend.deposit.application.DepositService;
import rs.unit.backend.deposit.application.DepositView;

@RestController
@RequestMapping("/api/deposits")
public class DepositController {

    private final DepositService service;

    public DepositController(DepositService service) {
        this.service = service;
    }

    @GetMapping("/current")
    public DepositView current(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.current(user);
    }

    @RestController
    @Profile("simulation")
    @RequestMapping("/api/deposits/current/banknotes")
    public static class BanknoteSimulationController {

        private final DepositService service;

        public BanknoteSimulationController(DepositService service) {
            this.service = service;
        }

        @PostMapping
        public BanknoteResult insert(
                @AuthenticationPrincipal AuthenticatedUser user,
                @Valid @RequestBody BanknoteRequest request) {
            return service.insert(
                    user, request.operationId(), request.currency(), request.denomination());
        }
    }

    public record BanknoteRequest(
            @NotNull UUID operationId,
            @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
            @Positive int denomination) {}
}
