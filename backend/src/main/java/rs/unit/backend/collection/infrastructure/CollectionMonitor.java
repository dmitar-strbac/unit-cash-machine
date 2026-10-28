package rs.unit.backend.collection.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import rs.unit.backend.collection.application.CollectionService;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        name = "unit.collection.monitor.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CollectionMonitor {

    private final CollectionService service;

    public CollectionMonitor(CollectionService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${unit.collection.poll-interval-ms:250}")
    public void synchronize() {
        service.synchronize();
    }
}
