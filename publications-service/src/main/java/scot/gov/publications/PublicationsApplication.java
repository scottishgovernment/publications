package scot.gov.publications;

import javax.inject.Inject;
import javax.ws.rs.core.Application;
import java.util.Collections;

import java.util.Set;

public class PublicationsApplication extends Application {

    @Inject
    PublicationsResource publicationsResource;

    @Override
    public Set<Object> getSingletons() {
        return Collections.singleton(publicationsResource);
    }
}