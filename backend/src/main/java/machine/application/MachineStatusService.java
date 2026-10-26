package rs.unit.backend.machine.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.port.HardwareStatusPort;
import rs.unit.backend.machine.domain.MachineReadinessPolicy;
import rs.unit.backend.machine.infrastructure.persistence.MachineConfiguration;
import rs.unit.backend.machine.infrastructure.persistence.MachineConfigurationRepository;

@Service
public class MachineStatusService {

    private static final short MACHINE_CONFIGURATION_ID = 1;

    private final MachineConfigurationRepository configurationRepository;
    private final HardwareStatusPort hardwareStatusPort;

    public MachineStatusService(
            MachineConfigurationRepository configurationRepository,
            HardwareStatusPort hardwareStatusPort) {
        this.configurationRepository = configurationRepository;
        this.hardwareStatusPort = hardwareStatusPort;
    }

    @Transactional(readOnly = true)
    public MachineStatus getStatus() {
        MachineConfiguration configuration =
                configurationRepository
                        .findById(MACHINE_CONFIGURATION_ID)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Machine configuration is missing."));

        HardwareSnapshot hardware = hardwareStatusPort.snapshot();

        return new MachineStatus(
                configuration.getMachineCode(),
                configuration.getDisplayName(),
                configuration.getLocation(),
                configuration.getCurrency(),
                configuration.getTimeZone(),
                true,
                MachineReadinessPolicy.isSecure(hardware),
                MachineReadinessPolicy.isDepositHardwareReady(hardware),
                MachineReadinessPolicy.isPrintingAvailable(hardware),
                hardware);
    }
}
