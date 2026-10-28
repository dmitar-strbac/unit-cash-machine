package rs.unit.backend.machine.application;

import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.unit.backend.collection.domain.CollectionStep;
import rs.unit.backend.collection.infrastructure.persistence.CollectionRepository;
import rs.unit.backend.diagnostics.domain.MachineDiagnosticsPolicy;
import rs.unit.backend.diagnostics.model.MachineAlert;
import rs.unit.backend.diagnostics.model.MachineAlert.Component;
import rs.unit.backend.diagnostics.model.MachineAlert.Severity;
import rs.unit.backend.diagnostics.model.MachineDiagnostics;
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
    private final CollectionRepository collectionRepository;

    public MachineStatusService(
            MachineConfigurationRepository configurationRepository,
            HardwareStatusPort hardwareStatusPort,
            CollectionRepository collectionRepository) {
        this.configurationRepository = configurationRepository;
        this.hardwareStatusPort = hardwareStatusPort;
        this.collectionRepository = collectionRepository;
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
        var alerts =
                new ArrayList<MachineAlert>(MachineDiagnosticsPolicy.evaluate(hardware).alerts());

        collectionRepository
                .findUnfinished()
                .ifPresent(
                        collection -> {
                            boolean incomplete = collection.step() == CollectionStep.INCOMPLETE;
                            boolean waitingForLock =
                                    incomplete && "LOCK_TIMEOUT".equals(collection.errorCode());
                            boolean requiresRecovery = incomplete && !waitingForLock;

                            alerts.add(
                                    new MachineAlert(
                                            requiresRecovery ? "CIT_INCOMPLETE" : "CIT_IN_PROGRESS",
                                            Component.LOCK,
                                            requiresRecovery ? Severity.ERROR : Severity.WARNING,
                                            requiresRecovery
                                                    ? "Cash collection requires recovery."
                                                    : waitingForLock
                                                            ? "Close and lock the door to complete cash collection."
                                                            : "Cash collection is in progress.",
                                            true));
                        });

        MachineDiagnostics diagnostics = new MachineDiagnostics(alerts);

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
                hardware,
                diagnostics.indicator(),
                diagnostics.alertCount(),
                diagnostics.alerts());
    }
}
