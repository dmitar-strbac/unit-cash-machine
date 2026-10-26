package rs.unit.backend.hardware.model;

import java.util.Objects;

public record HardwareSnapshot(
        AcceptorState acceptor,
        CashboxState cashbox,
        DoorState door,
        LockState lock,
        ExtensionBoardState extensionBoard,
        PrinterState printer) {

    public HardwareSnapshot {
        Objects.requireNonNull(acceptor, "acceptor");
        Objects.requireNonNull(cashbox, "cashbox");
        Objects.requireNonNull(door, "door");
        Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(extensionBoard, "extensionBoard");
        Objects.requireNonNull(printer, "printer");
    }

    public enum AcceptorState {
        READY,
        ACCEPTING,
        JAMMED,
        OFFLINE,
        UNKNOWN
    }

    public enum CashboxState {
        PRESENT,
        FULL,
        ABSENT,
        UNKNOWN
    }

    public enum DoorState {
        CLOSED,
        OPEN,
        UNKNOWN
    }

    public enum LockState {
        LOCKED,
        UNLOCKED,
        FAULT,
        UNKNOWN
    }

    public enum ExtensionBoardState {
        ONLINE,
        OFFLINE,
        UNKNOWN
    }

    public enum PrinterState {
        READY,
        OUT_OF_PAPER,
        OFFLINE,
        FAULT,
        UNKNOWN
    }
}
