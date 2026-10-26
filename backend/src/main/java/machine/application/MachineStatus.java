package rs.unit.backend.machine.application;

import rs.unit.backend.hardware.model.HardwareSnapshot;

public record MachineStatus(
        String machineCode,
        String displayName,
        String location,
        String currency,
        String timeZone,
        boolean simulated,
        boolean secure,
        boolean depositHardwareReady,
        boolean printingAvailable,
        HardwareSnapshot hardware) {}
