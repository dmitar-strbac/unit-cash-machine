package rs.unit.backend.hardware.port;

import rs.unit.backend.hardware.model.HardwareSnapshot;

public interface HardwareStatusPort {

    HardwareSnapshot snapshot();
}
