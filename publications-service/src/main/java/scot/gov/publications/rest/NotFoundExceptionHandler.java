package scot.gov.publications.rest;

import javax.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NotFoundExceptionHandler implements ExceptionMapper<NotFoundException> {

    @Inject
    public NotFoundExceptionHandler() {
        // default constructor for injection
    }

    /**
     * Convert the unhandled throwable into a 400 error with a generic message.
     *
     * @param t The unhandled throwable
     * @return Response with a 404 status and a generic message.
     */
    public Response toResponse(NotFoundException t) {
        return t.getResponse();
    }
}
