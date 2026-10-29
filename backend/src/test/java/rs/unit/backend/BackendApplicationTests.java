package rs.unit.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
            "unit.collection.monitor.enabled=false",
            "unit.deposit.monitor.enabled=false",
            "unit.maintenance.monitor.enabled=false"
        })
class BackendApplicationTests {

    @Test
    void contextLoads() {}
}
