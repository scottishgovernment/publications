package scot.gov.publications.rest;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;

public class ResponseLogger implements ContainerResponseFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ResponseLogger.class);

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
        String method = request.getRequest().getMethod();
        String path = request.getUriInfo().getPath();
        int status = response.getStatus();

        if (request.getProperty("stopwatch") != null) {
            StopWatch stopWatch = (StopWatch) request.getProperty("stopwatch");
            stopWatch.stop();
            LOG.info("{} {} {} {}",
                    status,
                    method,
                    path,
                    stopWatch.getTime());
        } else {
            LOG.info("{} {} {}",
                    status,
                    method,
                    path);
        }
    }

}
