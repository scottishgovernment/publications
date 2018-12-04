package scot.gov.publications.rest;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.Test;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RequestLoggerTest {

    @Test
    public void addsTimeIfStopwatchAvailable() throws Exception {
        // ARRAMGE
        RequestLogger sut = new RequestLogger();
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        Request request = mock(Request.class);
        when(requestContext.getRequest()).thenReturn(request);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        StopWatch stopWatch = mock(StopWatch.class);
        when(requestContext.getProperty("stopwatch")).thenReturn(stopWatch);

        // ACT
        sut.filter(requestContext, response);

        // ASSERT
        verify(stopWatch).stop();
    }

    @Test
    public void doesNotAddTimeTimeIfStopwatchUnavailable() throws Exception {
        // ARRAMGE
        RequestLogger sut = new RequestLogger();
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        Request request = mock(Request.class);
        when(requestContext.getRequest()).thenReturn(request);
        UriInfo uriInfo = mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(requestContext.getProperty("stopwatch")).thenReturn(null);

        // ACT
        sut.filter(requestContext, response);
    }
}
