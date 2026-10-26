package rs.unit.backend.diagnostics.model;

public record MachineAlert(
        String code, Component source, Severity severity, String message, boolean blocksDeposit) {

    public enum Component {
        ACCEPTOR,
        CASHBOX,
        DOOR,
        LOCK,
        EXTENSION_BOARD,
        PRINTER
    }

    public enum Severity {
        WARNING,
        ERROR
    }
}
