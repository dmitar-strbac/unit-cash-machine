package rs.unit.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "unit.collection.monitor.enabled=false")
class BackendApplicationTests {

    @Test
    void contextLoads() {}
}
