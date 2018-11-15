package scot.gov.publications;

import scot.gov.publications.rest.HealthCheckResource;
import scot.gov.publications.rest.PublicationsResource;

import javax.inject.Inject;
import javax.ws.rs.core.Application;
import java.util.Collections;

import java.util.HashSet;
import java.util.Set;

public class PublicationsApplication extends Application {

    @Inject
    PublicationsResource publicationsResource;

    @Inject
    HealthCheckResource healthCheckResource;

    @Override
    public Set<Object> getSingletons() {
        Set<Object> singletons = new HashSet<>();
        Collections.addAll(singletons,
                publicationsResource,
                healthCheckResource);
        return singletons;
    }
}