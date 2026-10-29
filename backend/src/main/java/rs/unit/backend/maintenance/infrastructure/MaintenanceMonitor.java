package rs.unit.backend.maintenance.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import rs.unit.backend.maintenance.application.MaintenanceService;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        name = "unit.maintenance.monitor.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MaintenanceMonitor {

    private final MaintenanceService service;

    public MaintenanceMonitor(MaintenanceService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${unit.maintenance.poll-interval-ms:250}")
    public void synchronize() {
        service.synchronize();
    }
}
