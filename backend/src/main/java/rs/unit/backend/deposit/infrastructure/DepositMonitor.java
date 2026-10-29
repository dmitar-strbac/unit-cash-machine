package rs.unit.backend.deposit.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import rs.unit.backend.deposit.application.DepositService;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        name = "unit.deposit.monitor.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DepositMonitor {

    private final DepositService deposits;

    public DepositMonitor(DepositService deposits) {
        this.deposits = deposits;
    }

    @Scheduled(fixedDelayString = "${unit.deposit.poll-interval-ms:1000}")
    public void reconcile() {
        deposits.reconcile();
    }
}
