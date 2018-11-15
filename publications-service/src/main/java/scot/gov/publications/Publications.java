package scot.gov.publications;

import dagger.ObjectGraph;
import io.undertow.Undertow;
import org.flywaydb.core.Flyway;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.InetSocketAddress;

/**
 * Main for the Publications service
 */
public class Publications {

    private static final Logger LOG = LoggerFactory.getLogger(Publications.class);

    @Inject
    PublicationsConfiguration config;

    @Inject
    PublicationsApplication application;

    public static final void main(String[] args) throws Exception {
        ObjectGraph graph = ObjectGraph.create(new PublicationsModule());

        // start the app
        graph.get(Publications.class).run();
    }

    public void run() {

        // run any required flyway migrations
        Flyway flyway = Flyway.configure()
                .dataSource(
                        config.getDatasource().getUrl(),
                        config.getDatasource().getUsername(),
                        config.getDatasource().getPassword())
                .load();
        flyway.migrate();

        // start the server
        Server server = new Server();
        server.deploy(application);
        server.start(Undertow.builder().addHttpListener(config.getPort(), "::"));
        LOG.info("Listening on port {}", server.port());
    }

    public static class Server extends UndertowJaxrsServer {
        public int port() {
            InetSocketAddress address = (InetSocketAddress) server
                    .getListenerInfo()
                    .get(0)
                    .getAddress();
            return address.getPort();
        }
    }
}