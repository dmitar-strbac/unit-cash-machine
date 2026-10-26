package rs.unit.backend.machine.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface MachineConfigurationRepository extends Repository<MachineConfiguration, Short> {

    Optional<MachineConfiguration> findById(Short id);
}
