package scot.gov.publications.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Top level exception handler for any uncaught exceptions.  This prevents any stack traces from being send to the
 * client.
 */
@Provider
public class TopLevelExceptionHandler implements ExceptionMapper<Throwable> {

    private static final Logger LOG = LoggerFactory.getLogger(TopLevelExceptionHandler.class);

    @Inject
    public TopLevelExceptionHandler() {
        // default constructor for injection
    }

    /**
     * Convert the unhandled throwable into a 400 error with a generic message.
     *
     * @param t The unhandled throwable
     * @return Response with a 400 status and a generic message.
     */
    public Response toResponse(Throwable t) {
        LOG.error("Unhandled throwable", t);
        return Response.status(500).build();
    }

}
