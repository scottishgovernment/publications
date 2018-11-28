package scot.gov.publications.rest;

import org.junit.Test;

import javax.ws.rs.WebApplicationException;

public class MaintenanceStatusTest {

    @Test(expected=WebApplicationException.class)
    public void assertAvailableThrowsExceptionInMaintenance() {
        MaintenanceStatus sut = new MaintenanceStatus();
        sut.startMaintainance();
        sut.assertAvailable();
    }

    @Test
    public void assertAvailableDoesNotThrowExceptionIfNotInMaintenance() {
        MaintenanceStatus sut = new MaintenanceStatus();
        sut.assertAvailable();
    }

}
