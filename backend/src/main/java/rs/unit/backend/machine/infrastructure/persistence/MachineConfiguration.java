package rs.unit.backend.machine.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "machine_configuration", schema = "cash_machine")
public class MachineConfiguration {

    @Id private Short id;

    @Column(name = "machine_code", nullable = false, length = 50)
    private String machineCode;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 200)
    private String location;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    protected MachineConfiguration() {}

    public Short getId() {
        return id;
    }

    public String getMachineCode() {
        return machineCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLocation() {
        return location;
    }

    public String getCurrency() {
        return currency;
    }

    public String getTimeZone() {
        return timeZone;
    }
}
