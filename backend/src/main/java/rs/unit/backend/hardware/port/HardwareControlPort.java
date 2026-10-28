package rs.unit.backend.hardware.port;

import rs.unit.backend.hardware.model.HardwareSnapshot;
import rs.unit.backend.hardware.model.PhysicalAction;

public interface HardwareControlPort extends HardwareStatusPort {

    HardwareSnapshot unlockDoor();

    HardwareSnapshot simulate(PhysicalAction action);
}
