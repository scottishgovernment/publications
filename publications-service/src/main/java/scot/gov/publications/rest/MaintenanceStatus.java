package scot.gov.publications.rest;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores status of the Maintenance service.
 */
public class MaintenanceStatus {

    private Object lock = new Object();
    private boolean inMaintenance = false;
    private LocalDateTime lastStartTime = null;
    private Map<String, String> data = new HashMap<>();

    @Inject
    public MaintenanceStatus() {
        // default constructor for injection
    }

    public void assertAvailable() {
        synchronized (lock) {
            if (inMaintenance) {
                Response response = Response
                        .status(500)
                        .entity("Service is in maintenance mode. Started: \" + lastStartTime")
                        .build();
                throw new WebApplicationException(response);
            }
        }
    }

    public void startMaintainance() {
        assertAvailable();

        synchronized (lock) {
            inMaintenance = true;
            lastStartTime = LocalDateTime.now();
            data.clear();
        }
    }

    public void endMaintainance(Map<String, String> data) {
        synchronized (lock) {
            this.data = new HashMap<>(data);
            inMaintenance = false;
        }
    }

    public boolean isInMaintenance() {
        return inMaintenance;
    }

    public LocalDateTime getLastStartTime() {
        return lastStartTime;
    }

    public Map<String, String> getData() {
        return data;
    }
}
